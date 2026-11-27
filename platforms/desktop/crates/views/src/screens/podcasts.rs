use gpui::prelude::*;
use gpui::{Context, Entity, FontWeight, Render, SharedString, Window, div};
use music::{Playlist, Track};
use state::{Io, Playback, Session};
use ui::{ActiveTheme as _, Button};

pub(crate) struct PodcastsView {
    session: Entity<Session>,
    playback: Entity<Playback>,
    tracks: Vec<Track>,
    categories: Vec<(String, Vec<Playlist>)>,
    selected_show: Option<String>,
    selected_show_id: Option<String>,
    loading: bool,
    error: Option<SharedString>,
}

impl PodcastsView {
    pub(crate) fn new(
        session: Entity<Session>,
        playback: Entity<Playback>,

        cx: &mut Context<Self>,
    ) -> Self {
        let mut view = Self {
            session: session.clone(),
            playback,
            tracks: Vec::new(),
            categories: Vec::new(),
            selected_show: None,
            selected_show_id: None,
            loading: false,
            error: None,
        };
        view.refresh(cx);
        cx.observe(&session, |this, _, cx| {
            this.refresh(cx);
        })
        .detach();
        view
    }

    pub(crate) fn refresh(&mut self, cx: &mut Context<Self>) {
        let io = Io::global(cx);
        let Some(client) = self.session.read(cx).client() else {
            self.tracks.clear();
            self.loading = false;
            self.error = Some("Connect YouTube Music to load saved podcast episodes".into());
            cx.notify();
            return;
        };
        self.loading = true;
        self.error = None;
        io.load(
            async move {
                let episodes = client.podcast_episodes().await;
                let categories = client.podcast_discovery_categories().await;
                Ok((episodes, categories))
            },
            |this: &mut Self, result, cx| {
                let (episodes, categories) = match result {
                    Ok(value) => value,
                    Err(error) => {
                        this.loading = false;
                        this.error = Some(format!("Could not load podcasts: {error:#}").into());
                        cx.notify();
                        return;
                    }
                };
                this.loading = false;
                match episodes {
                    Ok(tracks) => this.tracks = tracks,
                    Err(error) => {
                        this.error =
                            Some(format!("Could not load saved podcast episodes: {error:#}").into())
                    }
                }
                match categories {
                    Ok(categories) => this.categories = categories,
                    Err(error) if this.error.is_none() => {
                        this.error =
                            Some(format!("Could not load podcast discovery: {error:#}").into())
                    }
                    Err(_) => {}
                }
                cx.notify();
            },
            cx,
        )
        .detach();
    }
}

impl Render for PodcastsView {
    fn render(&mut self, _window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let theme = cx.theme();
        let mut page = div().id("podcasts-page").flex().flex_col().gap_4().p_6();
        page = page.child(
            div()
                .flex()
                .items_center()
                .justify_between()
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap_1()
                        .child(
                            div()
                                .text_2xl()
                                .font_weight(FontWeight::SEMIBOLD)
                                .child("Podcasts"),
                        )
                        .child(div().text_sm().text_color(theme.muted_foreground).child(
                            self.selected_show.clone().unwrap_or_else(|| {
                                "Discover shows and continue listening from YouTube Music"
                                    .to_string()
                            }),
                        )),
                )
                .child(
                    Button::new("refresh-podcasts")
                        .label("Refresh")
                        .on_click(cx.listener(|this, _, _, cx| {
                            this.refresh(cx);
                        })),
                ),
        );

        page = page.child(
            div()
                .pt_3()
                .text_lg()
                .font_weight(FontWeight::SEMIBOLD)
                .child("Discover podcasts"),
        );
        for (category_index, (category, shows)) in self.categories.iter().enumerate() {
            page = page.child(
                div()
                    .pt_2()
                    .text_base()
                    .font_weight(FontWeight::SEMIBOLD)
                    .child(category.clone()),
            );
            for (show_index, show) in shows.iter().enumerate() {
                let id = show.id.clone();
                let name = show.name.clone();
                let client = self.session.read(cx).client();
                page = page.child(
                    div()
                        .flex()
                        .items_center()
                        .justify_between()
                        .gap_3()
                        .py_1()
                        .child(div().flex_1().child(name.clone()))
                        .child(
                            Button::new(SharedString::from(format!(
                                "podcast-discover-{category_index}-{show_index}"
                            )))
                            .label("Open")
                            .on_click(cx.listener(
                                move |this, _, _, cx| {
                                    let Some(client) = client.clone() else {
                                        this.error =
                                            Some("Connect YouTube Music to open this show".into());
                                        cx.notify();
                                        return;
                                    };
                                    this.loading = true;
                                    let id = id.clone();
                                    let name = name.clone();
                                    let selected_id = id.clone();
                                    let io = Io::global(cx);
                                    io.load(
                                        async move { client.playlist_tracks(&id).await },
                                        move |this: &mut Self, result, cx| {
                                            this.loading = false;
                                            match result {
                                                Ok(tracks) => {
                                                    this.tracks = tracks;
                                                    this.selected_show = Some(name.clone());
                                                    this.selected_show_id =
                                                        Some(selected_id.clone());
                                                    this.error = None;
                                                }
                                                Err(error) => {
                                                    this.error = Some(
                                                        format!("Could not open {name}: {error:#}")
                                                            .into(),
                                                    )
                                                }
                                            }
                                            cx.notify();
                                        },
                                        cx,
                                    )
                                    .detach();
                                },
                            )),
                        ),
                );
            }
        }
        if self.selected_show.is_some() {
            page = page.child(
                Button::new("podcast-back-to-discovery")
                    .label("Back to discovery")
                    .on_click(cx.listener(|this, _, _, cx| {
                        this.selected_show = None;
                        this.selected_show_id = None;
                        this.tracks.clear();
                        cx.notify();
                    })),
            );
        }
        if self.loading {
            page = page.child(
                div()
                    .py_6()
                    .text_color(theme.muted_foreground)
                    .child("Loading saved episodesâ€¦"),
            );
        }
        if let Some(error) = self.error.clone() {
            page = page.child(div().py_6().text_color(theme.muted_foreground).child(error));
        }
        if !self.loading && self.error.is_none() && self.tracks.is_empty() {
            page = page.child(
                div()
                    .py_6()
                    .text_color(theme.muted_foreground)
                    .child("No saved podcast episodes were returned by this provider."),
            );
        }
        if !self.tracks.is_empty() {
            page = page.child(
                div()
                    .pt_4()
                    .text_lg()
                    .font_weight(FontWeight::SEMIBOLD)
                    .child("Episodes from YouTube Music"),
            );
        }
        for (index, track) in self.tracks.iter().cloned().enumerate() {
            let title = track.name.clone();
            let subtitle = track.artists.clone();
            page = page.child(
                div()
                    .id(SharedString::from(format!("podcast-episode-{index}")))
                    .flex()
                    .items_center()
                    .justify_between()
                    .gap_3()
                    .py_2()
                    .border_b_1()
                    .border_color(theme.border)
                    .child(
                        div()
                            .flex()
                            .flex_col()
                            .flex_1()
                            .gap_1()
                            .child(div().font_weight(FontWeight::MEDIUM).child(title))
                            .child(
                                div()
                                    .text_sm()
                                    .text_color(theme.muted_foreground)
                                    .child(subtitle),
                            ),
                    )
                    .child(
                        Button::new(SharedString::from(format!("play-podcast-{index}")))
                            .label("Play")
                            .on_click(cx.listener(move |this, _, _, cx| {
                                this.playback
                                    .update(cx, |playback, cx| playback.play(&track, cx));
                            })),
                    ),
            );
        }
        page
    }
}
