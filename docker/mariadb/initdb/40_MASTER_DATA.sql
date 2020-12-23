insert into CUSTOMER(
     CUSTOMER_ID,
     INSERT_DATE,
     INSERT_USER_ID,
     VERSION_NO,
     LOGICAL_DELETE_FLAG
)
VALUES (
    /*CUSTOMER_ID = */'dummy',
    sysdate(),
   'admin',
    0,
    0
);

insert into HOUSE_MEMBER_STORE(
   MEMBER_STORE_ID /*NVARCHAR2(15)  not null*/,
   MEMBER_STORE_NAME_JP /*NVARCHAR2(255)*/,
   MEMBER_STORE_NAME_EN /*NVARCHAR2(255)*/,
   TERMINAL_ID /*NVARCHAR2(255) not null*/,
   CLIENT_ID /*NVARCHAR2(255) not null*/,
   WALLET_SHOP_ID /*VARCHAR2(40)   not null*/,
   INSERT_DATE /*DATE           not null*/,
   INSERT_USER_ID /*NVARCHAR2(32)  not null*/,
   VERSION_NO /*NUMBER(9)      not null*/,
   LOGICAL_DELETE_FLAG /*NUMBER(1)      not null*/
)
VALUES (
    'dummy_member_id',
    'MEMBER_STORE_NAME_JP',
    'MEMBER_STORE_NAME_EN',
    'TERMINAL_ID',
    /* CLIENT_ID = */ 0,
    /*WALLET_SHOP_ID = */'000000000000000000000000000000000000002',
    sysdate(),
    'admin',
    0,
    0
);


insert into SERVICE_RELATION(
    SERVICE_RELATION_ID /*NUMBER        not null*/,
    FOREIGN_KEY_TYPE    /*NCHAR(2)      not null,*/,
    CUSTOMER_ID         /*CHAR(32)      not null*/,
    INSERT_DATE /*DATE           not null*/,
    INSERT_USER_ID /*NVARCHAR2(32)  not null*/,
    VERSION_NO /*NUMBER(9)      not null*/,
    LOGICAL_DELETE_FLAG /*NUMBER(1)      not null*/
)
    VALUES (
            /*SERVICE_RELATION_ID = */ 5,
            /* ISSUING_SERVICE */ '00',
            /*CUSTOMER_ID = */ 'dummy',
            sysdate(),
            'admin',
            0,
            0
    );

insert into ISSUING_SERVICE(
    CONTRACT_NUMBER /*CHAR(10)      not null*/,
    HOUSE_PAN         /*NVARCHAR2(16)*/,
    SERVICE_RELATION_ID /*NUMBER*/,
    INSERT_DATE /*DATE           not null*/,
    INSERT_USER_ID /*NVARCHAR2(32)  not null*/,
    VERSION_NO /*NUMBER(9)      not null*/,
    LOGICAL_DELETE_FLAG /*NUMBER(1)      not null*/
)
    VALUES (
            42,
            '1234567890123456',
            /*SERVICE_RELATION_ID = */5,
            sysdate(),
            'admin',
            0,
            0
    );
