package com.jvn.plugin.api.image;

/**
 * Validated numeric control exposed consistently to scripts and editor tools.
 * @param name stable script parameter name
 * @param label readable control label
 * @param defaultValue value used when no override is supplied
 * @param minimum inclusive lower bound
 * @param maximum inclusive upper bound
 * @param integer whether fractional values are rejected
 */
public record ImageEffectParameter(String name, String label, double defaultValue,
                                   double minimum, double maximum, boolean integer) {
  /** Validates the schema and its default value. */
  public ImageEffectParameter {
    if (name == null || !name.matches("[a-z][A-Za-z0-9_]*") || label == null || label.isBlank()
        || !Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum
        || !Double.isFinite(defaultValue) || defaultValue < minimum || defaultValue > maximum
        || (integer && defaultValue != Math.rint(defaultValue))) {
      throw new IllegalArgumentException("Invalid image effect parameter: " + name);
    }
  }
  /**
   * Checks one value against this control's range and integral requirement.
   * @param value supplied number
   * @return the unchanged validated number
   * @throws IllegalArgumentException if the number is non-finite or outside the schema
   */
  public double validate(double value) {
    if (!Double.isFinite(value) || value < minimum || value > maximum || (integer && value != Math.rint(value)))
      throw new IllegalArgumentException(label + " must be " + (integer ? "an integer " : "") + "between " + minimum + " and " + maximum);
    return value;
  }
}
