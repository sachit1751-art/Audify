use router::{Destination, LibraryTab, SettingsPage};

use super::expanded;

#[test]
fn a_section_expands_only_where_it_leads() {
    assert_eq!(
        expanded(&Destination::Library(LibraryTab::Albums)),
        (true, false)
    );
    assert_eq!(
        expanded(&Destination::Local(LibraryTab::Albums)),
        (false, true)
    );
    assert_eq!(
        expanded(&Destination::Settings(SettingsPage::Providers)),
        (false, false)
    );
}

#[test]
fn content_belongs_to_neither_section() {
    let away = [
        Destination::Home,
        Destination::Search,
        Destination::Album("id".into()),
        Destination::Playlist("id".into()),
        Destination::Artist("id".into()),
        Destination::Song("id".into()),
    ];

    for destination in away {
        assert_eq!(expanded(&destination), (false, false), "{destination:?}");
    }
}
