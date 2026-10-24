package com.skimsearch.indexing;

import com.skimsearch.indexing.IndexStore.BookIndexData;
import com.skimsearch.indexing.IndexStore.IndexResult;
import com.skimsearch.indexing.IndexStore.IndexStats;
import com.skimsearch.indexing.IndexStore.RebuildResult;

import java.util.Iterator;

/** Business logic: datalake -> tokenizer/metadata -> transactional store. */
public final class IndexingService {

    private final DatalakeReader datalake;
    private final IndexStore store;

    public IndexingService(DatalakeReader datalake, IndexStore store) {
        this.datalake = datalake;
        this.store = store;
    }

    /** Idempotent: calling it twice for the same book leaves the same index. */
    public IndexResult update(int bookId) {
        DatalakeReader.RawBook raw = datalake.read(bookId);
        return store.indexBook(MetadataExtractor.extract(bookId, raw.header()),
                Tokenizer.termFrequencies(raw.body()));
    }

    /** Rebuilds from every complete book; all-or-nothing. */
    public RebuildResult rebuild() {
        Iterator<Integer> ids = datalake.completeBookIds().iterator();
        Iterable<BookIndexData> books = () -> new Iterator<>() {
            public boolean hasNext() {
                return ids.hasNext();
            }

            public BookIndexData next() {
                DatalakeReader.RawBook raw = datalake.read(ids.next());
                return new BookIndexData(MetadataExtractor.extract(raw.bookId(), raw.header()),
                        Tokenizer.termFrequencies(raw.body()));
            }
        };
        return store.rebuild(books);
    }

    public IndexStats status() {
        return store.stats();
    }

    /** The book has not been downloaded at all (HTTP 404). */
    public static class BookNotFoundException extends RuntimeException {
        public BookNotFoundException(int bookId) {
            super("Book " + bookId + " is not in the datalake");
        }
    }

    /** The book is only partially downloaded: header or body missing (HTTP 409). */
    public static class BookNotReadyException extends RuntimeException {
        public BookNotReadyException(int bookId) {
            super("Book " + bookId + " is not ready: header or body is missing");
        }
    }
}
