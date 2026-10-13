# Simulated deposits and numbered transfers

Apply migrations through [004](../database/004_account_numbers_and_transfer_descriptions.sql)
before starting the application. Use the same origin and session cookie for every
Swagger request. Obtain `X-CSRF-TOKEN` from `GET /api/auth/csrf`, log in, then fetch
a fresh token. Swagger still requires manual CSRF input.

## Account selection and deposit

`GET /api/accounts` returns only your accounts, including their UUID `id` and
immutable 12-digit string `accountNumber`. Select the UUID of the account to fund.
The sequence can have gaps after failed transactions; numbers are never reused
or changed by renaming accounts.

Execute `POST /api/transactions/deposit` with the current CSRF token:

```json
{"accountId":"<your-account-uuid>","amount":200000}
```

The result is `201` and an immutable SUCCESS/DEPOSIT transaction. Follow its
`Location` to retrieve details, or read the account balance. No OTP, bank details
or payment provider is involved. Every successful deposit call credits again;
do not automatically retry a deposit after an ambiguous network failure.

## Preview, edit and transfer

Use a recipient's **account number**, not their UUID. Preview with
`POST /api/transactions/transfer/preview`:

```json
{
  "sourceAccountId":"<your-account-uuid>",
  "destinationAccountNumber":"100000000001",
  "amount":50000
}
```

Preview returns the source ID/number, recipient number and display name, amount,
currency and resolved description. It validates funds and recipient existence
without reserving funds, writing history or consuming an idempotency key. Recipient
email, balance and private account label are not exposed. A source and destination
may belong to the same user, but must be distinct accounts.

The default message is `"<sender User.displayName> đã chuyển khoản"`. The persisted
user's name is used, independently of the account label and original holder-name
snapshot. Missing, null and blank descriptions select default mode. To change the
message, add `"description":"Tiền ăn trưa"` before executing the transfer. Text is
stripped at both ends, supports Vietnamese and is limited to 255 characters.

Send the final body to `POST /api/transactions/transfer` with the current CSRF token
and one `Idempotency-Key`, for example a newly generated UUID. It independently
rechecks everything, even if preview was skipped. A successful new transfer returns
`201` and `Location`. There is no transfer OTP. Messages cannot be edited afterward.

For a retry, keep **both key and request body unchanged**. Completed retries return
`200` with the original transaction ID, time and description; no money moves again.
Integral amounts such as `50000.0` and `50000` are equivalent. Surrounding message
whitespace is ignored. Default mode remains stable if the sender's display name
later changes; sending explicit text equal to the displayed default is still a
different request mode. A changed payload with a completed key returns
`409 IDEMPOTENCY_KEY_REUSED`.

Keys are case-sensitive, 1-128 visible ASCII characters, without whitespace or
implicit trimming. Only successful keys persist, indefinitely. After a rejected or
rolled-back attempt the key can be retried with valid parameters. After application
restart, log in again before replaying; keys survive, sessions do not.

## Details, history and errors

- `GET /api/transactions/{id}`: only an owner of either participating account.
- `GET /api/accounts/{id}/transactions?page=0&size=20`: only the account owner;
  size 1-100; ordered by createdAt DESC, id DESC. Each transfer appears once for
  each participant, with the same ID and immutable content.
- Transaction fields: id, type, sourceAccountId, destinationAccountId,
  sourceAccountNumber, destinationAccountNumber, amount, currency, status,
  createdAt and description. Inapplicable sides and deposit/legacy descriptions
  are null. Failed attempts never appear in history.
- Money must be a JSON number, positive, mathematically integral VND and no greater
  than `9999999999999999999`. Fractional values are rejected without rounding.
- `400 INVALID_REQUEST`: malformed JSON, unknown fields, amount, recipient number,
  message, key or pagination; `404 ACCOUNT_NOT_FOUND`: a missing participating
  account; `404 TRANSACTION_NOT_FOUND`: missing transaction.
- `401 AUTHENTICATION_REQUIRED`, `403 CSRF_INVALID`, `403 ACCESS_DENIED`: session,
  CSRF or ownership failure. All POST routes, including preview, require CSRF.
- `422`: INSUFFICIENT_FUNDS, SAME_ACCOUNT_TRANSFER or BALANCE_LIMIT_EXCEEDED.
- `409 CONCURRENCY_CONFLICT`: rolled-back lock timeout/deadlock or unresolved race;
  retry a transfer with the same key/body. Account locks time out after 5 seconds.
- `500 INTERNAL_ERROR`: unexpected failure; no partial balance, history or key
  writes commit. Responses do not reveal SQL or exception details.

Withdrawal APIs, a frontend and automatic Swagger CSRF handling remain pending.
