let authId = 0;
const ErrorCodes = {
    ok: "00000"
};

module.exports = (req, res, next) => {
    console.log({
        path: req.url,
        body: req.body
    });
    if (req.method === 'POST' && req.url === '/payment') {
        const body = {};

        // 取引特定情報
        body.intranid = `${Date.now()}`; // dummy
        // 承認番号
        body.authId = `${authId++}`;
        //エラーコード
        body.rErrcode = ErrorCodes.ok;

        res.send(body);
        return;
    }
    // Continue to JSON Server router
    next()
};
