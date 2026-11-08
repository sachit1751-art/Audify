import { Download } from 'lucide-react';

/** A compact download affordance inspired by React Bits' Status Mark pending state. */
export function DownloadStatusMark() {
  return (
    <span className="download-status-mark" role="img" aria-label="Download">
      <svg className="download-status-orbit" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <circle className="download-status-ring" cx="12" cy="12" r="9" />
      </svg>
      <Download className="download-status-arrow" size={13} strokeWidth={2.2} aria-hidden="true" />
    </span>
  );
}
