# Moon Rite

The first Tama is obtained through a ritual instead of a crafting recipe.

## Player Flow

1. Find a **still water source block** at night.
2. Make sure the sky is open and the moon is visible.
3. Right-click the water with a feather.
4. The feather is consumed and becomes the **Bone Tama**.

## Design Notes

- The ritual is intentionally simple to read and easy to remember.
- It only works on source water, not flowing water.
- The server owns the rule check and item conversion.
- The client receives a VFX packet and renders the moon reflection/ripple effect.
- Water itself is left alone; the effect is an overlay so the magic feels anchored to the scene.

## Why This Approach

This is the onboarding moment for the mod's magic. The moon reflection matters more than an item recipe here, so the implementation favors a visible, world-based ritual over a hidden inventory action.

The feather-in-water version is the baseline. If we want a more dramatic ceremony later, we can add a floating feather or Tama animation on top of the same trigger.
