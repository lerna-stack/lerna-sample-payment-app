CREATE TABLE SERVICE_RELATION (
  SERVICE_RELATION_ID DECIMAL NOT NULL,
  FOREIGN_KEY_TYPE CHAR(2) NOT NULL,
  CUSTOMER_ID CHAR(32) NOT NULL,
  INSERT_DATE DATETIME NOT NULL,
  INSERT_USER_ID VARCHAR(32) NOT NULL,
  UPDATE_DATE DATETIME,
  UPDATE_USER_ID VARCHAR(32),
  VERSION_NO DECIMAL(9) NOT NULL,
  LOGICAL_DELETE_FLAG DECIMAL(1) NOT NULL
);

CREATE SEQUENCE SERVICE_RELATION_SEQ MINVALUE 1 MAXVALUE 9999999999 increment by 1 start with 1;
