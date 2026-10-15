create index IF NOT EXISTS fpi_comp_idx on feed_page_item(seq_no, sist_endret, kilde);

cluster feed_page_item using fpi_comp_idx;
analyze feed_page_item;
