use gpui::prelude::*;
use gpui::{
    App, Context, Entity, FocusHandle, Global, Render, SharedString, Task, Window, div, px,
};
use i18n::t;
use music::Track;
use state::{AppSettings, Audify, SkipSegment};
use ui::{
    ActiveTheme as _, Button, Dismiss, FORM_CONTEXT, Input, Modal, Scrubber, ScrubberState, Submit,
    Text,
};

const WAVEFORM_BINS: usize = 180;

pub(crate) struct SkipEditor {
    track: Option<Track>,
    settings: Entity<AppSettings>,
    start: Entity<Input>,
    end: Entity<Input>,
    start_ms: u64,
    end_ms: u64,
    editing: Option<usize>,
    segments: Vec<SkipSegment>,
    peaks: Vec<f32>,
    loading: bool,
    load: Option<Task<()>>,
    start_handle: ScrubberState,
    end_handle: ScrubberState,
    focus: FocusHandle,
    restore: Option<FocusHandle>,
}

struct Installed(Entity<SkipEditor>);

impl Global for Installed {}

impl SkipEditor {
    pub fn entity(cx: &mut App) -> Entity<Self> {
        if cx.try_global::<Installed>().is_none() {
            let settings = Audify::global(cx).settings.clone();
            let editor = cx.new(|cx| Self {
                track: None,
                settings,
                start: cx.new(|cx| Input::new("skip-segment-start", cx).compact()),
                end: cx.new(|cx| Input::new("skip-segment-end", cx).compact()),
                start_ms: 0,
                end_ms: 0,
                editing: None,
                segments: Vec::new(),
                peaks: Vec::new(),
                loading: false,
                load: None,
                start_handle: ScrubberState::new("skip-segment-start-handle"),
                end_handle: ScrubberState::new("skip-segment-end-handle"),
                focus: cx.focus_handle(),
                restore: None,
            });
            cx.set_global(Installed(editor));
        }
        cx.global::<Installed>().0.clone()
    }

    pub fn open(track: Track, window: &mut Window, cx: &mut App) {
        Self::entity(cx).update(cx, |this, cx| this.show(track, window, cx));
    }

    fn show(&mut self, track: Track, window: &mut Window, cx: &mut Context<Self>) {
        let id = track.id.clone().unwrap_or_default();
        let existing = self.settings.read(cx).skip_segments(&id).to_vec();
        self.segments = existing.clone();
        self.editing = (!existing.is_empty()).then_some(0);
        let selected = existing.first().copied().unwrap_or(SkipSegment {
            start_ms: 0,
            end_ms: track.duration.as_millis().min(u64::MAX as u128) as u64,
        });
        self.start_ms = selected.start_ms;
        self.end_ms = selected.end_ms;
        self.start
            .update(cx, |input, cx| input.set_text(clock(self.start_ms), cx));
        self.end
            .update(cx, |input, cx| input.set_text(clock(self.end_ms), cx));
        self.peaks.clear();
        self.loading = false;
        self.load = None;
        self.restore = window.focused(cx);
        window.focus(&self.focus, cx);
        self.track = Some(track.clone());
        cx.notify();

        if let Some(id) = track.id.filter(|id| music::is_local_id(id)) {
            let duration = track.duration;
            self.loading = true;
            self.load = Some(cx.spawn(async move |this, cx| {
                let decoded = cx
                    .background_spawn(async move {
                        music::local::waveform(&id, duration, WAVEFORM_BINS)
                    })
                    .await;
                this.update(cx, |this, cx| {
                    this.peaks = decoded.unwrap_or_default();
                    this.loading = false;
                    cx.notify();
                })
                .ok();
            }));
        }
    }

    fn close(&mut self, window: &mut Window, cx: &mut Context<Self>) {
        self.track = None;
        self.load = None;
        if let Some(focus) = self.restore.take() {
            window.focus(&focus, cx);
        }
        cx.notify();
    }

    fn set_start(&mut self, fraction: f32, cx: &mut Context<Self>) {
        let duration = self.duration_ms();
        self.start_ms = (duration as f32 * fraction.clamp(0., 1.)) as u64;
        self.start_ms = self.start_ms.min(self.end_ms.saturating_sub(1));
        self.start
            .update(cx, |input, cx| input.set_text(clock(self.start_ms), cx));
        cx.notify();
    }

    fn set_end(&mut self, fraction: f32, cx: &mut Context<Self>) {
        let duration = self.duration_ms();
        self.end_ms = ((duration as f32 * fraction.clamp(0., 1.)) as u64)
            .max(self.start_ms.saturating_add(1))
            .min(duration);
        self.end
            .update(cx, |input, cx| input.set_text(clock(self.end_ms), cx));
        cx.notify();
    }

    fn duration_ms(&self) -> u64 {
        self.track.as_ref().map_or(0, |track| {
            track.duration.as_millis().min(u64::MAX as u128) as u64
        })
    }

    fn save(&mut self, window: &mut Window, cx: &mut Context<Self>) {
        let Some(track) = self.track.as_ref() else {
            return;
        };
        let Some(id) = track.id.clone() else { return };
        let Some(start) = parse_clock(self.start.read(cx).text()) else {
            self.start
                .update(cx, |input, cx| input.set_invalid(true, cx));
            return;
        };
        let Some(end) = parse_clock(self.end.read(cx).text()) else {
            self.end.update(cx, |input, cx| input.set_invalid(true, cx));
            return;
        };
        let duration = self.duration_ms();
        if start >= end || end > duration {
            self.start
                .update(cx, |input, cx| input.set_invalid(start >= end, cx));
            self.end.update(cx, |input, cx| {
                input.set_invalid(end > duration || start >= end, cx)
            });
            return;
        }
        let mut segments = self.settings.read(cx).skip_segments(&id).to_vec();
        let segment = SkipSegment {
            start_ms: start,
            end_ms: end,
        };
        if let Some(index) = self.editing.filter(|index| *index < segments.len()) {
            segments[index] = segment;
        } else {
            segments.push(segment);
        }
        self.settings.update(cx, |settings, cx| {
            settings.set_skip_segments(id, segments, cx)
        });
        self.close(window, cx);
    }

    fn add_another(&mut self, cx: &mut Context<Self>) {
        self.editing = None;
        self.start_ms = 0;
        self.end_ms = self.duration_ms();
        self.start
            .update(cx, |input, cx| input.set_text(clock(self.start_ms), cx));
        self.end
            .update(cx, |input, cx| input.set_text(clock(self.end_ms), cx));
        cx.notify();
    }

    fn select_segment(&mut self, index: usize, segment: SkipSegment, cx: &mut Context<Self>) {
        self.editing = Some(index);
        self.start_ms = segment.start_ms;
        self.end_ms = segment.end_ms;
        self.start
            .update(cx, |input, cx| input.set_text(clock(self.start_ms), cx));
        self.end
            .update(cx, |input, cx| input.set_text(clock(self.end_ms), cx));
        cx.notify();
    }

    fn clear(&mut self, window: &mut Window, cx: &mut Context<Self>) {
        let Some(id) = self.track.as_ref().and_then(|track| track.id.clone()) else {
            return;
        };
        self.settings.update(cx, |settings, cx| {
            settings.set_skip_segments(id, Vec::new(), cx)
        });
        self.close(window, cx);
    }

    fn waveform(&self, cx: &Context<Self>) -> impl IntoElement {
        let theme = *cx.theme();
        let duration = self.duration_ms().max(1);
        let start = self.start_ms as f32 / duration as f32;
        let end = self.end_ms as f32 / duration as f32;
        let bars = div()
            .flex()
            .items_center()
            .justify_between()
            .gap(px(1.))
            .h(px(62.))
            .w_full()
            .children(self.peaks.iter().enumerate().map(|(index, peak)| {
                let fraction = index as f32 / self.peaks.len().max(1) as f32;
                let selected = fraction >= start && fraction <= end;
                div()
                    .flex_1()
                    .min_w(px(1.))
                    .h(px(4. + peak * 54.))
                    .rounded(px(2.))
                    .bg(if selected {
                        theme.progress_bar
                    } else {
                        theme.muted_foreground.opacity(0.72)
                    })
            }));
        let handles = div()
            .absolute()
            .inset_0()
            .child(
                Scrubber::new(&self.start_handle, start)
                    .name("Skip segment start")
                    .colors(
                        theme.foreground.opacity(0.),
                        theme.foreground.opacity(0.),
                        theme.foreground,
                    )
                    .on_move(
                        cx.listener(|this, fraction: &f32, _, cx| this.set_start(*fraction, cx)),
                    ),
            )
            .child(
                Scrubber::new(&self.end_handle, end)
                    .name("Skip segment end")
                    .colors(
                        theme.foreground.opacity(0.),
                        theme.foreground.opacity(0.),
                        theme.foreground,
                    )
                    .on_move(
                        cx.listener(|this, fraction: &f32, _, cx| this.set_end(*fraction, cx)),
                    ),
            );
        div()
            .relative()
            .w_full()
            .h(px(62.))
            .child(bars)
            .child(handles)
    }
}

impl Render for SkipEditor {
    fn render(&mut self, _window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let Some(track) = self.track.as_ref() else {
            return div().into_any_element();
        };
        let theme = *cx.theme();
        let duration = self.duration_ms();
        let existing = track
            .id
            .as_ref()
            .map_or(0, |id| self.settings.read(cx).skip_segments(id).len());
        let local = track.id.as_deref().is_some_and(music::is_local_id);
        div()
            .absolute()
            .inset_0()
            .key_context(FORM_CONTEXT)
            .track_focus(&self.focus)
            .on_action(cx.listener(|this, _: &Dismiss, window, cx| {
                cx.stop_propagation();
                this.close(window, cx);
            }))
            .on_action(cx.listener(|this, _: &Submit, window, cx| {
                cx.stop_propagation();
                this.save(window, cx);
            }))
            .child(
                Modal::new("skip-segment-editor", "Skip segments")
                    .w(theme.metrics.cover * 3.3)
                    .detail(SharedString::from(track.name.clone()))
                    .child(self.waveform(cx))
                    .when(self.loading, |modal| {
                        modal.child(
                            div()
                                .text_size(theme.text(Text::Small))
                                .text_color(theme.muted_foreground)
                                .child("Decoding audio waveform…"),
                        )
                    })
                    .when(!self.loading && self.peaks.is_empty(), |modal| {
                        modal.child(
                            div()
                                .text_size(theme.text(Text::Small))
                                .text_color(theme.muted_foreground)
                                .child(if local {
                                    "This local track could not be decoded for its waveform."
                                } else {
                                    "A full waveform is currently available for local audio files."
                                }),
                        )
                    })
                    .child(
                        div()
                            .flex()
                            .gap_3()
                            .child(
                                div()
                                    .flex_1()
                                    .flex_col()
                                    .gap_1()
                                    .child("Start")
                                    .child(self.start.clone()),
                            )
                            .child(
                                div()
                                    .flex_1()
                                    .flex_col()
                                    .gap_1()
                                    .child("End")
                                    .child(self.end.clone()),
                            ),
                    )
                    .child(
                        div()
                            .text_size(theme.text(Text::Small))
                            .text_color(theme.muted_foreground)
                            .child(format!(
                                "Track duration: {} · {} existing skip segment(s)",
                                clock(duration),
                                existing
                            )),
                    )
                    .when(!self.segments.is_empty(), |modal| {
                        modal.child(
                            div().flex().flex_wrap().gap_1().children(
                                self.segments.iter().copied().enumerate().map(
                                    |(index, segment)| {
                                        Button::new(("skip-segment-existing", index))
                                            .small()
                                            .ghost()
                                            .selected(self.editing == Some(index))
                                            .label(format!(
                                                "{}–{}",
                                                clock(segment.start_ms),
                                                clock(segment.end_ms)
                                            ))
                                            .on_click(cx.listener(move |this, _, _, cx| {
                                                this.select_segment(index, segment, cx)
                                            }))
                                    },
                                ),
                            ),
                        )
                    })
                    .action(
                        Button::new("add-another-skip-segment")
                            .ghost()
                            .label("Add another")
                            .on_click(cx.listener(|this, _, _, cx| this.add_another(cx))),
                    )
                    .action(
                        Button::new("cancel-skip-segment")
                            .ghost()
                            .label(t!("common-cancel"))
                            .on_click(cx.listener(|this, _, window, cx| this.close(window, cx))),
                    )
                    .when(existing > 0, |modal| {
                        modal.action(
                            Button::new("clear-skip-segments")
                                .ghost()
                                .label("Clear all")
                                .on_click(
                                    cx.listener(|this, _, window, cx| this.clear(window, cx)),
                                ),
                        )
                    })
                    .action(
                        Button::new("save-skip-segment")
                            .primary()
                            .label(t!("common-save"))
                            .on_click(cx.listener(|this, _, window, cx| this.save(window, cx))),
                    )
                    .on_dismiss(cx.listener(|this, _, window, cx| this.close(window, cx))),
            )
            .into_any_element()
    }
}

fn clock(milliseconds: u64) -> String {
    let seconds = milliseconds / 1000;
    format!("{}:{:02}", seconds / 60, seconds % 60)
}

fn parse_clock(value: &str) -> Option<u64> {
    let mut parts = value.trim().split(':');
    let minutes = parts.next()?.parse::<u64>().ok()?;
    let seconds = parts.next()?.parse::<u64>().ok()?;
    if parts.next().is_some() || seconds >= 60 {
        return None;
    }
    Some(
        minutes
            .saturating_mul(60_000)
            .saturating_add(seconds.saturating_mul(1000)),
    )
}
