ALTER TABLE SALES_DETAIL ADD
CONSTRAINT SYS_C0038684
FOREIGN KEY (
  CUSTOMER_ID
) REFERENCES CUSTOMER (
  CUSTOMER_ID
) ON UPDATE CASCADE ON DELETE RESTRICT;
