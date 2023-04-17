/* feed_page_item representerer det som ligger på feeden. */
create table feed_page_item(
    id uuid primary key,
    sist_endret timestamp with time zone not null default current_timestamp,
    seq_no bigserial not null,
    feed_item_id uuid not null,
    status varchar(50) not null,
    title varchar(500) not null,
    business_name varchar(1000) not null,
    municipal varchar(200) not null
);

/* feed-item er 1-m mot feed_page_item: hvis en stillingsannonse blir oppdatert så vil
   feed-item bli oppdatert til å inneholde siste versjon, mens feed_page_item vil inneholde
   noen av de historiske dataene
   */
create table feed_item(
    id uuid primary key,
    json text not null,
    sist_endret timestamp with time zone not null default current_timestamp
);