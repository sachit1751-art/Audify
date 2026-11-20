// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
import { useEffect, useRef } from 'react';
import { Navigate, useParams } from 'react-router';
import { PageHeader } from '@/components/PageHeader';
import { CircleLockup, SquareLockup, VideoLockup } from '@/components/music/Lockups';
import { EmptyState, LoadError, LoadMoreError, NeedsExtension } from '@/components/music/States';
import { TrackList } from '@/components/music/TrackList';
import { isLibrarySection, LIBRARY_SECTIONS } from '@/data/music/library';
import { useLibrary, useSearch, useSignedIn, useYouTubeReady } from '@/data/music/queries';
import { useYtAuth } from '@/extension/useYtAuth';
import { PlainSkeleton } from '@/skeletons/PageSkeletons';
import { Shelves } from '@/components/music/ShelfView';
import type { Shelf } from '@/data/music/types';
import { seeAllGrid, seeAllTracks } from './pageStyles';

/** Asks for the next page when [target] scrolls into view. */
function useLoadMore(onVisible: (() => void) | null) {
  const target = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const node = target.current;
    if (!node || !onVisible) return;
    const observer = new IntersectionObserver((entries) => entries.some((e) => e.isIntersecting) && onVisible(), {
      rootMargin: '600px 0px',
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, [onVisible]);
  return target;
}

/** A library section (prompt §5.3): Recently Played, Artists, Albums, Songs, Made for You, All Playlists, Podcasts. */
const PODCAST_SEARCHES = [
  ["Trending podcasts", "trending podcasts"],
  ["News podcasts", "news podcasts"],
  ["Comedy podcasts", "comedy podcasts"],
  ["True crime podcasts", "true crime podcasts"],
  ["Science & technology podcasts", "science technology podcasts"],
  ["Education podcasts", "educational podcasts"],
] as const;

/** Live category-specific YouTube Music search shelves, not an official curated directory. */
function PodcastDiscovery() {
  const trending = useSearch(PODCAST_SEARCHES[0][1]);
  const news = useSearch(PODCAST_SEARCHES[1][1]);
  const comedy = useSearch(PODCAST_SEARCHES[2][1]);
  const trueCrime = useSearch(PODCAST_SEARCHES[3][1]);
  const science = useSearch(PODCAST_SEARCHES[4][1]);
  const education = useSearch(PODCAST_SEARCHES[5][1]);
  const results = [trending, news, comedy, trueCrime, science, education];
  const shelves: Shelf[] = PODCAST_SEARCHES.flatMap(([title], index) => {
    const items = results[index]?.data?.playlists ?? [];
    return items.length ? [{ title, strapline: 'YouTube Music search results', layout: 'square', items, seeAll: null }] : [];
  });
  const loading = results.some((result) => result.isLoading);
  const failed = results.some((result) => result.isError);
  return (
    <section aria-label="Discover podcasts" className="mb-8">
      <h2 className="mx-(--body-gutter) mt-5 mb-3 type-title-2-emphasized">Discover podcasts</h2>
      {loading && !shelves.length && <PlainSkeleton />}
      <Shelves shelves={shelves} />
      {failed && <p className="mx-(--body-gutter) mt-3 type-footnote text-secondary">Some discovery categories could not load. Your saved podcast library is still available.</p>}
    </section>
  );
}
export default function LibraryPage() {
  const section = useParams().section ?? '';
  const { missing } = useYouTubeReady();
  const auth = useYtAuth();
  const signedIn = useSignedIn();
  const library = useLibrary(isLibrarySection(section) ? section : 'playlists');
  const loadMore = useLoadMore(
    library.hasNextPage && !library.isFetchingNextPage && !library.isFetchNextPageError
      ? () => void library.fetchNextPage()
      : null,
  );

  if (!isLibrarySection(section)) return <Navigate to="/home" replace />;
  const title = LIBRARY_SECTIONS[section].title;
  if (missing) return <NeedsExtension what="your library" />;
  if (auth.state === 'ready' && !signedIn) {
    return (
      <>
        <PageHeader title={title} />
        <EmptyState
          title="Sign in to see your library"
          detail="Audify uses the YouTube Music account this browser is signed in to. Sign in on music.youtube.com, then come back."
        />
      </>
    );
  }
  if (library.isError && !library.data) return <LoadError error={library.error} onRetry={() => void library.refetch()} />;
  if (!library.data) return <PlainSkeleton />;

  const grid = library.data.pages.flatMap((page) => page.data.grid);
  const tracks = library.data.pages.flatMap((page) => page.data.tracks);
  return (
    <>
      <PageHeader title={title} />
      {section === "podcasts" && <PodcastDiscovery />}
      {!grid.length && !tracks.length && <EmptyState title="Nothing here yet" />}
      {grid.length > 0 && (
        <ul className={seeAllGrid} data-wide={grid.every((i) => i.wide) || undefined}>
          {grid.map((item, i) => (
            <li key={`${item.id}-${i}`}>
              {item.kind === 'artist' ? (
                <CircleLockup item={item} />
              ) : item.wide ? (
                <VideoLockup item={item} />
              ) : (
                <SquareLockup item={item} />
              )}
            </li>
          ))}
        </ul>
      )}
      {tracks.length > 0 && (
        <div className={seeAllTracks}>
          <TrackList
            tracks={tracks}
            variant="playlist"
            source={{ title, type: 'library', id: section }}
            onNearEnd={
              library.hasNextPage && !library.isFetchingNextPage && !library.isFetchNextPageError
                ? () => void library.fetchNextPage()
                : undefined
            }
          />
        </div>
      )}
      {grid.length > 0 && <div ref={loadMore} aria-hidden="true" />}
      {library.isFetchNextPageError && <LoadMoreError onRetry={() => void library.fetchNextPage()} />}
    </>
  );
}
