ALTER TABLE SERVICE_RELATION ADD
CONSTRAINT SYS_C0038685
FOREIGN KEY (
  CUSTOMER_ID
) REFERENCES CUSTOMER (
  CUSTOMER_ID
) ON UPDATE CASCADE ON DELETE RESTRICT;
