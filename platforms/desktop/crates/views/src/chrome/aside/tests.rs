use std::time::Duration;

use music::{LyricsLane, LyricsLine, LyricsWord, Voice};
use ui::Springs;

use super::{
    QueuePosition, Sections, Slot, active_lyrics_row, anchored_lyrics_offset,
    background_line_singing, karaoke_fragments, karaoke_window, lag_spring, line_has_passed,
    line_row, lyric_row_count, plain_lyrics_fragments, primary_karaoke_fade,
    primary_karaoke_visible, secondary_karaoke_visible, wrap_fragment_widths,
};
use gpui::px;
use ui::Motion;

fn slots(sections: Sections) -> Vec<Slot> {
    (0..sections.len())
        .filter_map(|i| sections.slot(i))
        .collect()
}

#[test]
fn lays_out_every_section() {
    let sections = Sections::new(
        2,
        true,
        [
            state::QueueKind::Context,
            state::QueueKind::Queued,
            state::QueueKind::Context,
            state::QueueKind::Suggested,
        ],
    );

    assert_eq!(sections.current_index(), Some(4));
    assert_eq!(
        slots(sections),
        [
            Slot::Header("queue-history"),
            Slot::Track(QueuePosition::Past(0)),
            Slot::Track(QueuePosition::Past(1)),
            Slot::Header("queue-now-playing"),
            Slot::Track(QueuePosition::Current),
            Slot::Header("queue-up-next"),
            Slot::Track(QueuePosition::Upcoming(0)),
            Slot::Header("queue-next-in-queue"),
            Slot::Track(QueuePosition::Upcoming(1)),
            Slot::Header("queue-up-next"),
            Slot::Track(QueuePosition::Upcoming(2)),
            Slot::Header("queue-similar"),
            Slot::Track(QueuePosition::Upcoming(3)),
        ]
    );
}

#[test]
fn suggests_similar_tracks_without_anything_up_next() {
    let sections = Sections::new(0, true, [state::QueueKind::Suggested]);

    assert_eq!(
        slots(sections),
        [
            Slot::Header("queue-now-playing"),
            Slot::Track(QueuePosition::Current),
            Slot::Header("queue-similar"),
            Slot::Track(QueuePosition::Upcoming(0)),
        ]
    );
}

#[test]
fn drops_headers_for_empty_sections() {
    let sections = Sections::new(0, true, [state::QueueKind::Context]);

    assert_eq!(sections.current_index(), Some(1));
    assert_eq!(
        slots(sections),
        [
            Slot::Header("queue-now-playing"),
            Slot::Track(QueuePosition::Current),
            Slot::Header("queue-up-next"),
            Slot::Track(QueuePosition::Upcoming(0)),
        ]
    );
}

#[test]
fn lays_out_history_without_a_current_track() {
    let sections = Sections::new(1, false, []);

    assert_eq!(sections.current_index(), None);
    assert_eq!(
        slots(sections),
        [
            Slot::Header("queue-history"),
            Slot::Track(QueuePosition::Past(0))
        ]
    );
}

#[test]
fn an_empty_queue_has_no_rows() {
    let sections = Sections::new(0, false, []);

    assert_eq!(sections.len(), 0);
    assert_eq!(sections.current_index(), None);
}

#[test]
fn a_long_instrumental_pause_gets_its_own_lyrics_row() {
    let lines = [
        LyricsLine {
            start: Duration::from_secs(2),
            end: Some(Duration::from_secs(5)),
            text: "first".to_owned(),
            romanized: None,
            words: None,
            secondary: Vec::new(),
            voice: Voice::Lead,
        },
        LyricsLine {
            start: Duration::from_secs(12),
            end: Some(Duration::from_secs(15)),
            text: "second".to_owned(),
            romanized: None,
            words: None,
            secondary: Vec::new(),
            voice: Voice::Lead,
        },
    ];

    assert_eq!(lyric_row_count(&lines), 3);
    assert_eq!(line_row(&lines, 0), 0);
    assert_eq!(line_row(&lines, 1), 2);
    assert_eq!(active_lyrics_row(&lines, Duration::from_secs(8)), Some(1));
    assert_eq!(active_lyrics_row(&lines, Duration::from_secs(13)), Some(2));
}

#[test]
fn word_timing_exposes_a_pause_hidden_by_the_line_end() {
    let lines = [
        LyricsLine {
            start: Duration::from_secs(2),
            end: Some(Duration::from_secs(12)),
            text: "first".to_owned(),
            romanized: None,
            words: Some(vec![LyricsWord {
                start: Duration::from_secs(2),
                end: Duration::from_secs(5),
                text: "first".to_owned(),
            }]),
            secondary: Vec::new(),
            voice: Voice::Lead,
        },
        LyricsLine {
            start: Duration::from_secs(12),
            end: Some(Duration::from_secs(15)),
            text: "second".to_owned(),
            romanized: None,
            words: None,
            secondary: Vec::new(),
            voice: Voice::Lead,
        },
    ];

    assert_eq!(lyric_row_count(&lines), 3);
    assert_eq!(active_lyrics_row(&lines, Duration::from_secs(8)), Some(1));
    assert!(line_has_passed(&lines[0], Duration::from_secs(8)));
}

#[test]
fn lyrics_follow_uses_unscrolled_item_bounds() {
    let offset = anchored_lyrics_offset(px(0.), px(200.), px(100.), px(500.));

    assert_eq!(offset, px(-170.));
}

#[test]
fn lyrics_follow_preserves_a_subpixel_target() {
    let offset = anchored_lyrics_offset(px(0.25), px(200.125), px(100.5), px(500.));

    assert!((offset.as_f32() - -169.725).abs() < 0.001);
}

#[test]
fn karaoke_uses_spacing_from_the_complete_line() {
    let text = "I said oooh I'm drowning in the night";
    let words = ["I", "said", "oooh", "I'm", "drowning", "in", "the", "night"]
        .into_iter()
        .enumerate()
        .map(|(index, text)| LyricsWord {
            start: Duration::from_millis(index as u64 * 100),
            end: Duration::from_millis(index as u64 * 100 + 100),
            text: text.to_owned(),
        })
        .collect::<Vec<_>>();

    let fragments = karaoke_fragments(text, &words);

    assert_eq!(fragments.concat(), text);
    assert_eq!(
        fragments,
        [
            "I ",
            "said ",
            "oooh ",
            "I'm ",
            "drowning ",
            "in ",
            "the ",
            "night"
        ]
    );
}

#[test]
fn plain_lyrics_keep_spacing_in_breakable_fragments() {
    let fragments = plain_lyrics_fragments("Ладони полны слёзок, но время");

    assert_eq!(fragments.concat(), "Ладони полны слёзок, но время");
    assert_eq!(fragments, ["Ладони ", "полны ", "слёзок, ", "но ", "время"]);
}

#[test]
fn lyrics_wrap_at_the_active_size_plan() {
    let rows = wrap_fragment_widths(&[px(40.), px(35.), px(30.), px(20.)], &[true; 4], px(80.));

    assert_eq!(rows, [0..2, 2..4]);
}

#[test]
fn lyrics_keep_an_oversized_fragment_on_its_own_row() {
    let rows = wrap_fragment_widths(&[px(120.), px(30.), px(30.)], &[true; 3], px(80.));

    assert_eq!(rows, [0..1, 1..3]);
}

#[test]
fn lyrics_row_springs_stagger_without_changing_their_damping_ratio() {
    let (first_frequency, first_ratio) = lag_spring(0.).canonical();
    let (last_frequency, last_ratio) = lag_spring(1.).canonical();

    assert!(first_frequency > last_frequency);
    assert!((first_ratio - last_ratio).abs() < f32::EPSILON);
    assert!(
        first_ratio < 1.,
        "the lyrics settle should have a subtle overshoot"
    );
}

#[test]
fn lyrics_keep_their_tuned_spring_presets() {
    assert_eq!(Springs::LYRICS_SCROLL.stiffness, 170.);
    assert_eq!(Springs::LYRICS_SCROLL.damping, 23.);
    assert_eq!(Springs::LYRICS_SCROLL.mass, 1.);
    assert_eq!(Springs::LYRICS_ROW.stiffness, 210.);
    assert_eq!(Springs::LYRICS_ROW.damping, 22.);
    assert_eq!(Springs::LYRICS_ROW.mass, 1.);
}

#[test]
fn a_late_first_word_uses_the_whole_lead_in() {
    let words = vec![
        LyricsWord {
            start: Duration::from_millis(1500),
            end: Duration::from_millis(1900),
            text: "first".to_owned(),
        },
        LyricsWord {
            start: Duration::from_millis(2000),
            end: Duration::from_millis(2400),
            text: "second".to_owned(),
        },
    ];

    assert_eq!(
        karaoke_window(Duration::from_millis(1000), &words, 0),
        (Duration::from_millis(1000), Duration::from_millis(1900))
    );
}

#[test]
fn a_finished_background_lane_stays_sung_until_its_line_departs() {
    let lane = LyricsLane {
        start: Duration::from_secs(2),
        end: Some(Duration::from_secs(3)),
        text: "(E)".to_owned(),
        romanized: None,
        words: Some(vec![LyricsWord {
            start: Duration::from_secs(2),
            end: Duration::from_secs(3),
            text: "(E)".to_owned(),
        }]),
    };

    assert!(!secondary_karaoke_visible(
        &lane,
        true,
        Duration::from_millis(1999)
    ));
    assert!(secondary_karaoke_visible(
        &lane,
        true,
        Duration::from_secs(2)
    ));
    assert!(secondary_karaoke_visible(
        &lane,
        true,
        Duration::from_secs(4)
    ));
    assert!(!secondary_karaoke_visible(
        &lane,
        false,
        Duration::from_secs(4)
    ));
}

#[test]
fn an_overlapped_primary_line_keeps_singing_in_the_background() {
    let line = LyricsLine {
        start: Duration::from_secs(2),
        end: Some(Duration::from_secs(8)),
        text: "Wake me up inside".to_owned(),
        romanized: None,
        words: Some(vec![LyricsWord {
            start: Duration::from_secs(2),
            end: Duration::from_secs(8),
            text: "Wake me up inside".to_owned(),
        }]),
        secondary: Vec::new(),
        voice: Voice::Lead,
    };

    assert!(!primary_karaoke_visible(
        &line,
        false,
        Duration::from_millis(1999)
    ));
    assert!(primary_karaoke_visible(
        &line,
        false,
        Duration::from_secs(5)
    ));
    assert!(primary_karaoke_visible(
        &line,
        false,
        Duration::from_secs(8)
    ));
    assert!(!primary_karaoke_visible(
        &line,
        false,
        Duration::from_secs(8) + Motion::Base.span()
    ));
}

#[test]
fn the_active_primary_line_keeps_its_completed_sweep_until_departure() {
    let line = LyricsLine {
        start: Duration::from_secs(2),
        end: Some(Duration::from_secs(5)),
        text: "line".to_owned(),
        romanized: None,
        words: Some(vec![LyricsWord {
            start: Duration::from_secs(2),
            end: Duration::from_secs(5),
            text: "line".to_owned(),
        }]),
        secondary: Vec::new(),
        voice: Voice::Lead,
    };

    assert!(primary_karaoke_visible(&line, true, Duration::from_secs(8)));
}

#[test]
fn a_finished_background_line_fades_from_white_to_gray() {
    let line = LyricsLine {
        start: Duration::from_secs(2),
        end: Some(Duration::from_secs(8)),
        text: "Wake me up inside".to_owned(),
        romanized: None,
        words: Some(vec![LyricsWord {
            start: Duration::from_secs(2),
            end: Duration::from_secs(8),
            text: "Wake me up inside".to_owned(),
        }]),
        secondary: Vec::new(),
        voice: Voice::Lead,
    };
    let fade = Motion::Control.span();

    assert_eq!(
        primary_karaoke_fade(&line, false, Duration::from_millis(7999)),
        0.
    );
    assert_eq!(
        primary_karaoke_fade(&line, false, Duration::from_secs(8) + fade / 2),
        0.5
    );
    assert_eq!(
        primary_karaoke_fade(&line, false, Duration::from_secs(8) + fade),
        1.
    );
    assert_eq!(
        primary_karaoke_fade(&line, true, Duration::from_secs(8) + fade),
        0.
    );
}

#[test]
fn only_a_currently_singing_background_line_gets_the_reduced_blur() {
    let line = LyricsLine {
        start: Duration::from_secs(2),
        end: Some(Duration::from_secs(8)),
        text: "Wake me up inside".to_owned(),
        romanized: None,
        words: Some(vec![LyricsWord {
            start: Duration::from_secs(2),
            end: Duration::from_secs(8),
            text: "Wake me up inside".to_owned(),
        }]),
        secondary: Vec::new(),
        voice: Voice::Lead,
    };

    assert!(!background_line_singing(
        &line,
        false,
        Duration::from_millis(1999)
    ));
    assert!(background_line_singing(
        &line,
        false,
        Duration::from_secs(5)
    ));
    assert!(!background_line_singing(
        &line,
        false,
        Duration::from_secs(8)
    ));
    assert!(!background_line_singing(
        &line,
        true,
        Duration::from_secs(5)
    ));
}

#[test]
fn a_finished_line_stays_past_during_a_gap() {
    let line = LyricsLine {
        start: Duration::from_secs(2),
        end: Some(Duration::from_secs(5)),
        text: "line".to_owned(),
        romanized: None,
        words: None,
        secondary: Vec::new(),
        voice: Voice::Lead,
    };

    assert!(line_has_passed(&line, Duration::from_secs(8)));
}
