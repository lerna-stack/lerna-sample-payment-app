CREATE TABLE INCENTIVE_MASTER (
  INCENTIVE_MASTER_ID DECIMAL NOT NULL,
  SETTLEMENT_TYPE CHAR(2) NOT NULL,
  INCENTIVE_TYPE CHAR(2) NOT NULL,
  INCENTIVE_RATE DECIMAl(5) DEFAULT NULL,
  INCENTIVE_AMOUNT DECIMAL DEFAULT NULL,
  INCENTIVE_DATE_FROM DATETIME NOT NULL,
  INCENTIVE_DATE_TO DATETIME NOT NULL,
  INSERT_DATE DATETIME NOT NULL,
  INSERT_USER_ID VARCHAR(32) NOT NULL,
  UPDATE_DATE DATETIME,
  UPDATE_USER_ID VARCHAR(32),
  VERSION_NO DECIMAL(9) NOT NULL,
  LOGICAL_DELETE_FLAG DECIMAL(1) NOT NULL
);

CREATE SEQUENCE INCENTIVE_MASTER_SEQ MINVALUE 1 MAXVALUE 9999999999 increment by 1 start with 1;
