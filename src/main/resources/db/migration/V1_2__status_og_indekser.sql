alter table feed_item add column status varchar(50) not null default 'INACTIVE';
create index IF NOT EXISTS fpi_fi_idx on feed_page_item(feed_item_id);
create unique index IF NOT EXISTS fpi_seqno_idx on feed_page_item(seq_no);
