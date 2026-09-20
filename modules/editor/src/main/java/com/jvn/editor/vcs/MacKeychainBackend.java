package com.jvn.editor.vcs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import org.jspecify.annotations.Nullable;

/**
 * Stores the GitHub token in the macOS login Keychain as a generic password, keyed by
 * service+account, via a direct binding to Security.framework's SecItem* C API.
 *
 * <p>JNA-Platform does not ship a ready-made high-level Keychain wrapper the way it does for
 * Windows Credential Manager, so this binds directly to {@code Security.framework}'s
 * {@code SecItemAdd}/{@code SecItemCopyMatching}/{@code SecItemUpdate}/{@code SecItemDelete} C
 * API, plus the small slice of {@code CoreFoundation} (CFString/CFData/CFDictionary) needed to
 * build the query dictionaries those calls expect.
 */
final class MacKeychainBackend implements CredentialBackend {
  static final String SERVICE = "jvn-editor-github-token";
  static final String ACCOUNT = "github-token";

  private final String service;
  private final String account;

  MacKeychainBackend() {
    this(SERVICE, ACCOUNT);
  }

  MacKeychainBackend(String service, String account) {
    this.service = service;
    this.account = account;
  }

  private Pointer createDictionary(Pointer[] keys, Pointer[] values) {
    var library = com.sun.jna.NativeLibrary.getInstance("CoreFoundation");
    return CoreFoundation.INSTANCE.CFDictionaryCreate(Pointer.NULL, keys, values, keys.length,
        library.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks"),
        library.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks"));
  }

  private static final int ERR_SEC_SUCCESS = 0;
  private static final int ERR_SEC_ITEM_NOT_FOUND = -25300;
  private static final int ERR_SEC_DUPLICATE_ITEM = -25299;

  private static final int CF_STRING_ENCODING_UTF8 = 0x08000100;

  interface CoreFoundation extends Library {
    CoreFoundation INSTANCE = Native.load("CoreFoundation", CoreFoundation.class);

    Pointer CFStringCreateWithCString(Pointer allocator, String cStr, int encoding);

    Pointer CFDataCreate(Pointer allocator, byte[] bytes, long length);

    Pointer CFDictionaryCreate(Pointer allocator, Pointer[] keys, Pointer[] values, long numValues,
        Pointer keyCallBacks, Pointer valueCallBacks);

    long CFDictionaryGetCount(Pointer theDict);

    void CFDictionaryGetKeysAndValues(Pointer theDict, Pointer[] keys, Pointer[] values);

    void CFRelease(Pointer cf);

    long CFDataGetLength(Pointer data);

    Pointer CFDataGetBytePtr(Pointer data);
  }

  interface Security extends Library {
    Security INSTANCE = Native.load("Security", Security.class);

    int SecItemAdd(Pointer query, @Nullable PointerByReference result);

    int SecItemCopyMatching(Pointer query, PointerByReference result);

    int SecItemUpdate(Pointer query, Pointer attributesToUpdate);

    int SecItemDelete(Pointer query);
  }

  private Pointer cfString(String value) {
    return CoreFoundation.INSTANCE.CFStringCreateWithCString(Pointer.NULL, value, CF_STRING_ENCODING_UTF8);
  }

  private Pointer cfKeyConstant(String symbolName) {
    return com.sun.jna.NativeLibrary.getInstance("Security").getGlobalVariableAddress(symbolName).getPointer(0);
  }

  private Pointer buildBaseQuery(String service, String account) {
    Pointer kSecClass = cfKeyConstant("kSecClass");
    Pointer kSecClassGenericPassword = cfKeyConstant("kSecClassGenericPassword");
    Pointer kSecAttrService = cfKeyConstant("kSecAttrService");
    Pointer kSecAttrAccount = cfKeyConstant("kSecAttrAccount");

    // cfString(...) returns an owned (+1) CFStringRef via CFStringCreateWithCString.
    // CFDictionaryCreate below retains its own copy of every value it is given (default
    // CFType value callbacks), so our original references must be released afterwards to
    // avoid leaking them — the dictionary's own retain is all CFRelease(query) later balances.
    Pointer serviceString = cfString(service);
    Pointer accountString = cfString(account);
    try {
      Pointer[] keys = { kSecClass, kSecAttrService, kSecAttrAccount };
      Pointer[] values = { kSecClassGenericPassword, serviceString, accountString };
      return createDictionary(keys, values);
    } finally {
      CoreFoundation.INSTANCE.CFRelease(serviceString);
      CoreFoundation.INSTANCE.CFRelease(accountString);
    }
  }

  /**
   * Builds a new {@code CFDictionaryRef} containing the union of {@code a}'s and {@code b}'s
   * key/value pairs. The Core Foundation C API has no in-place dictionary merge, so this
   * enumerates each source dictionary's entries via {@code CFDictionaryGetKeysAndValues} and
   * passes the concatenated key/value arrays to {@code CFDictionaryCreate}, which copies (and,
   * per the CFType default callbacks, CFRetains) every key/value it is given. Callers remain
   * responsible for releasing {@code a} and {@code b} themselves — this method does not consume
   * or release either input, only reference their contents, and returns a dictionary the caller
   * must independently release.
   */
  private Pointer mergeDictionaries(Pointer a, Pointer b) {
    int countA = (int) CoreFoundation.INSTANCE.CFDictionaryGetCount(a);
    int countB = (int) CoreFoundation.INSTANCE.CFDictionaryGetCount(b);

    Pointer[] keysA = new Pointer[countA];
    Pointer[] valuesA = new Pointer[countA];
    if (countA > 0) {
      CoreFoundation.INSTANCE.CFDictionaryGetKeysAndValues(a, keysA, valuesA);
    }

    Pointer[] keysB = new Pointer[countB];
    Pointer[] valuesB = new Pointer[countB];
    if (countB > 0) {
      CoreFoundation.INSTANCE.CFDictionaryGetKeysAndValues(b, keysB, valuesB);
    }

    Pointer[] mergedKeys = new Pointer[countA + countB];
    Pointer[] mergedValues = new Pointer[countA + countB];
    System.arraycopy(keysA, 0, mergedKeys, 0, countA);
    System.arraycopy(valuesA, 0, mergedValues, 0, countA);
    System.arraycopy(keysB, 0, mergedKeys, countA, countB);
    System.arraycopy(valuesB, 0, mergedValues, countA, countB);

    return createDictionary(mergedKeys, mergedValues);
  }

  @Override
  public boolean exists() {
    try {
      return load().isPresent();
    } catch (IOException ex) {
      return false;
    }
  }

  @Override
  public Optional<String> load() throws IOException {
    Pointer query = buildBaseQuery(service, account);
    Pointer extraAttrs = null;
    Pointer fullQuery = null;
    try {
      Pointer kSecReturnData = cfKeyConstant("kSecReturnData");
      Pointer kCFBooleanTrue = com.sun.jna.NativeLibrary.getInstance("CoreFoundation")
          .getGlobalVariableAddress("kCFBooleanTrue").getPointer(0);
      Pointer kSecMatchLimit = cfKeyConstant("kSecMatchLimit");
      Pointer kSecMatchLimitOne = cfKeyConstant("kSecMatchLimitOne");

      Pointer[] keys = { kSecReturnData, kSecMatchLimit };
      Pointer[] values = { kCFBooleanTrue, kSecMatchLimitOne };
      extraAttrs = createDictionary(keys, values);

      fullQuery = mergeDictionaries(query, extraAttrs);
      PointerByReference resultRef = new PointerByReference();
      int status = Security.INSTANCE.SecItemCopyMatching(fullQuery, resultRef);

      if (status == ERR_SEC_ITEM_NOT_FOUND) return Optional.empty();
      if (status != ERR_SEC_SUCCESS) {
        throw new IOException("GitHub token load failed (macOS Keychain): OSStatus " + status);
      }

      // With only kSecReturnData requested (a single return-type key), SecItemCopyMatching
      // hands back the raw CFDataRef directly rather than wrapping it in a result dictionary.
      Pointer data = resultRef.getValue();
      if (data == null) {
        return Optional.empty();
      }
      try {
        long length = CoreFoundation.INSTANCE.CFDataGetLength(data);
        Pointer bytePtr = CoreFoundation.INSTANCE.CFDataGetBytePtr(data);
        byte[] bytes = bytePtr.getByteArray(0, (int) length);
        return Optional.of(new String(bytes, StandardCharsets.UTF_8));
      } finally {
        CoreFoundation.INSTANCE.CFRelease(data);
      }
    } finally {
      if (fullQuery != null) CoreFoundation.INSTANCE.CFRelease(fullQuery);
      if (extraAttrs != null) CoreFoundation.INSTANCE.CFRelease(extraAttrs);
      CoreFoundation.INSTANCE.CFRelease(query);
    }
  }

  @Override
  public void save(String token) throws IOException {
    if (token == null || token.isBlank()) throw new IllegalArgumentException("Token cannot be empty.");
    byte[] tokenBytes = token.trim().getBytes(StandardCharsets.UTF_8);

    Pointer query = buildBaseQuery(service, account);
    Pointer valueData = null;
    Pointer attributesToUpdate = null;
    Pointer addQuery = null;
    try {
      Pointer kSecValueData = cfKeyConstant("kSecValueData");
      valueData = CoreFoundation.INSTANCE.CFDataCreate(Pointer.NULL, tokenBytes, tokenBytes.length);

      Pointer[] updateKeys = { kSecValueData };
      Pointer[] updateValues = { valueData };
      attributesToUpdate = createDictionary(updateKeys, updateValues);

      int updateStatus = Security.INSTANCE.SecItemUpdate(query, attributesToUpdate);
      if (updateStatus == ERR_SEC_ITEM_NOT_FOUND) {
        addQuery = mergeDictionaries(query, attributesToUpdate);
        int addStatus = Security.INSTANCE.SecItemAdd(addQuery, null);
        if (addStatus != ERR_SEC_SUCCESS && addStatus != ERR_SEC_DUPLICATE_ITEM) {
          throw new IOException("GitHub token save failed (macOS Keychain): OSStatus " + addStatus);
        }
      } else if (updateStatus != ERR_SEC_SUCCESS) {
        throw new IOException("GitHub token save failed (macOS Keychain): OSStatus " + updateStatus);
      }
    } finally {
      if (addQuery != null) CoreFoundation.INSTANCE.CFRelease(addQuery);
      if (attributesToUpdate != null) CoreFoundation.INSTANCE.CFRelease(attributesToUpdate);
      if (valueData != null) CoreFoundation.INSTANCE.CFRelease(valueData);
      CoreFoundation.INSTANCE.CFRelease(query);
    }
  }

  @Override
  public void clear() throws IOException {
    Pointer query = buildBaseQuery(service, account);
    try {
      int status = Security.INSTANCE.SecItemDelete(query);
      if (status != ERR_SEC_SUCCESS && status != ERR_SEC_ITEM_NOT_FOUND) {
        throw new IOException("GitHub token clear failed (macOS Keychain): OSStatus " + status);
      }
    } finally {
      CoreFoundation.INSTANCE.CFRelease(query);
    }
  }
}
