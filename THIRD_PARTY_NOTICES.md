# Third-party source notices

##  MOTION lyrics renderer

The `app/src/main/java/com/motion/lyrics/` renderer is adapted from the
project: <https://github.com/rezapranata089-bit/>.

The upstream repository and this project are licensed under GNU GPL version 3.
The Audify adaptation retains the upstream implementation's attribution in the
source files and is distributed under the same license. The renderer is wired
to Audify's existing `LyricLine` timing model and player settings.

##  Home layout and elastic refresh adaptations

The Home layout persistence model/editor and elastic pull-to-refresh interaction
in `shared` and `sharedUi` are adapted from the  project:
<https://github.com/rezapranata089-bit/>.

These adaptations are distributed under GNU GPL version 3, consistent with
's upstream licence and Audify's project licence. The implementations
are integrated with Audify's existing Home shelf model, preferences and
Compose navigation rather than copied as standalone screens.
