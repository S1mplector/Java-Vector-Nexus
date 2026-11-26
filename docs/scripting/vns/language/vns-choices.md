# VNS Choices & Branching

Choices are how players influence story flow. VNS supports multi-line choices, inline choices, conditional visibility, and several branching patterns.

---

## Multi-Line Choice Syntax

Each choice line starts with `>`. Consecutive choice lines are grouped into a single choice node.

```text
> Choice text
> Choice text -> targetLabel
> Choice text -> targetLabel [if condition]
```

**Basic example:**

```vns
narrator: What do you want to do?

> Explore the forest -> forest
> Visit the town -> town
> Rest at camp -> camp

@label forest
narrator: You head into the dense forest.
[end]

@label town
narrator: The town bustles with activity.
[end]

@label camp
narrator: You set up camp for the night.
[end]
```

### Choices Without Targets

Choices without `-> label` simply continue to the next line after the choice node:

```vns
narrator: What's your favorite color?

> Red
> Blue
> Green

narrator: Interesting choice.
```

### Choices With Targets

The `-> label` suffix jumps to the named label when selected:

```vns
> Go left -> path_left
> Go right -> path_right
> Turn back -> retreat
```

---

## Inline Choice Syntax

For compact one-liner choice blocks:

```text
[choice Text1->label1 | Text2->label2 | Text3->label3]
```

**Examples:**

```vns
[choice Continue->next | Exit->ending]

[choice Fight->battle | Run->escape | Negotiate->talk]
```

Inline choices also support condition suffixes:

```vns
[choice Continue->next [if flags.ready] | Exit->end]
```

---

## Conditional Choices

Both multi-line and inline choices support condition suffixes with `[if expression]`. Choices whose condition evaluates to false are **hidden** from the player.

### Multi-line conditional:

```vns
narrator: What will you do?

> Storm the castle -> siege [if army_size >= 100]
> Sneak through the sewers -> infiltrate [if has_map]
> Negotiate peace -> negotiate
> Retreat -> retreat
```

Only choices whose conditions are true (or that have no condition) are shown.

### Inline conditional:

```vns
[choice Attack->fight [if weapon_equipped] | Flee->run | Surrender->give_up]
```

### Condition syntax reference

Conditions use the same expression language as `[if]` blocks:

```text
flags.ready
score >= 10 && lives > 0
not seen_intro
(playerClass == "mage" and mana >= 20) or debug
has_key
!locked_door
inventory_count > 0
```

See [Variables & Conditions](vns-variables.md) for the full condition reference.

---

## Branching Patterns

### Pattern 1: Simple Branch

The most common pattern — player picks a path, each leads somewhere different.

```vns
@scenario simple_branch
@character narrator "Narrator"

@label start
narrator: The road forks ahead.

> Take the mountain path -> mountains
> Follow the river -> river

@label mountains
narrator: The air grows thin as you climb.
[end]

@label river
narrator: The water sparkles in the sunlight.
[end]
```

### Pattern 2: Branch and Merge

Paths diverge but rejoin later. Use variables to track which path was taken.

```vns
@label crossroads
narrator: Two doors stand before you.

> Red door -> red_door
> Blue door -> blue_door

@label red_door
[set chose_red true]
narrator: The red room is warm and inviting.
[jump merged]

@label blue_door
[set chose_blue true]
narrator: The blue room is cool and calm.
[jump merged]

@label merged
narrator: You emerge on the other side.
[if chose_red goto red_callback]
[if chose_blue goto blue_callback]

@label red_callback
narrator: The warmth still lingers in your heart.
[jump continue]

@label blue_callback
narrator: A chill runs down your spine.
[jump continue]

@label continue
narrator: The journey continues.
[end]
```

### Pattern 3: Hub with Repeating Choices

A central hub the player returns to, with choices that unlock/change over time.

```vns
@label hub
narrator: You're in the village square.

> Visit the blacksmith -> blacksmith [if !sword_bought]
> Visit the blacksmith (sword purchased) -> blacksmith_done [if sword_bought]
> Enter the tavern -> tavern
> Talk to the elder -> elder [if elder_available]
> Leave the village -> depart [if quest_complete]

@label blacksmith
[set sword_bought true]
narrator: You purchase a fine sword.
[jump hub]

@label blacksmith_done
narrator: The blacksmith waves. Nothing more to buy.
[jump hub]

@label tavern
[set elder_available true]
narrator: The barkeep mentions the elder wants to see you.
[jump hub]

@label elder
[set quest_complete true]
narrator: The elder gives you the quest artifact.
[jump hub]

@label depart
narrator: With quest complete, you set off.
[end]
```

### Pattern 4: Score-Gated Endings

Accumulate points through choices and gate the ending.

```vns
@var score = 0

@label question_1
narrator: What is the capital of France?

> Paris -> q1_correct
> London -> q1_wrong

@label q1_correct
[inc score 1]
narrator: Correct!
[jump question_2]

@label q1_wrong
narrator: Not quite.
[jump question_2]

@label question_2
narrator: What color is the sky?

> Blue -> q2_correct
> Green -> q2_wrong

@label q2_correct
[inc score 1]
narrator: Right!
[jump results]

@label q2_wrong
narrator: Nope.
[jump results]

@label results
narrator: Your score: ${score} out of 2.
[if score >= 2 goto perfect]
[if score == 1 goto okay]
[jump fail]

@label perfect
narrator: Perfect score!
[end]

@label okay
narrator: Not bad.
[end]

@label fail
narrator: Better luck next time.
[end]
```

### Pattern 5: Timed/Limited Choices

Use variables to track attempt counts:

```vns
@var attempts = 3

@label try_door
narrator: The locked door stands before you. (${attempts} attempts remaining)

[if attempts <= 0 goto locked_out]

> Try the brass key -> try_brass
> Try the silver key -> try_silver
> Give up -> locked_out

@label try_brass
[dec attempts 1]
narrator: The brass key doesn't fit.
[jump try_door]

@label try_silver
narrator: The silver key turns! The door opens.
[jump success]

@label locked_out
narrator: You've exhausted your attempts. The door remains sealed.
[end]

@label success
narrator: Beyond the door lies treasure.
[end]
```

### Pattern 6: Nested Choices

Choices can lead to more choices before resolving:

```vns
@label negotiate
narrator: The merchant eyes you carefully.

> Offer gold -> offer_gold
> Offer a favor -> offer_favor

@label offer_gold
narrator: How much gold?

> 50 coins -> pay_50 [if coins >= 50]
> 100 coins -> pay_100 [if coins >= 100]
> Nevermind -> negotiate

@label pay_50
[dec coins 50]
narrator: The merchant reluctantly agrees.
[jump deal_done]

@label pay_100
[dec coins 100]
narrator: The merchant beams with delight.
[set merchant_happy true]
[jump deal_done]

@label offer_favor
narrator: The merchant asks you to deliver a package.
[set has_delivery_quest true]
[jump deal_done]

@label deal_done
narrator: The deal is struck.
```

---

## Choice Parsing Details

- Multi-line choices (`> ...`) are **buffered** until a non-choice line appears, then flushed as one choice node.
- Inline choices (`[choice ... | ...]`) are parsed and emitted immediately.
- Both support optional `-> target` and optional trailing `[if condition]`.
- Choice text supports `${variable}` interpolation at runtime.

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [Variables & Conditions](vns-variables.md) — condition expression reference
- [Subroutines & Flow Control](../flow/vns-flow-control.md) — jumps and label patterns
- [Commands Reference](vns-commands.md) — full command list
