# Contributing to Flush Frames

Thanks for helping out! Bug reports, ideas and pull requests are all welcome.

## Reporting a bug

Open an issue with:
- Minecraft, Fabric Loader and Fabric API versions
- What you did, what you expected, and what happened
- Screenshots help a lot - turning on the `looking_at_item_frame` debug entry (F3 + F6) and F3 + B
  hitboxes shows the frame's facing, position and whether it's attached

## Making changes

1. Fork the repo and create a branch.
2. Make your change. Keep to the existing style: one `if (block instanceof X)` per block in the
   placement mixins, and short comments only where something isn't obvious.
3. Run the in-game test and make sure it passes:
   ```bash
   ./gradlew runClientGameTest
   ```
   If you add support for a new block, add a case for it to the test.
4. Open a pull request describing what changed and why.

## Project layout

- `src/main/java/com/h3xbit/flushframes/mixin/` - the mixins
  - `AttachItemFrameMixin` / `AttachPaintingMixin` - where frames and paintings sit on each block
  - `DoorFrameMixin` - re-places frames and paintings when a door or trapdoor toggles
  - `ItemFrameFrameDelayMixin` / `PaintingFrameDelayMixin` - draw them from the same block state as
    the block model on screen, so they move in the same frame
  - `BannerFrameMixin` - banner support
- `src/gametest/` - the in-game test
