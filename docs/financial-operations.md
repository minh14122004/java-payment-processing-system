# Simulated deposits, idempotent withdrawals and numbered transfers

Apply migrations through [005](../database/005_idempotency_operation_namespaces.sql)
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
  createdAt and description. Inapplicable sides and deposit/withdrawal/legacy descriptions
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
  retry a transfer or withdrawal with the same key/body. Account locks time out after 5 seconds.
- `500 INTERNAL_ERROR`: unexpected failure; no partial balance, history or key
  writes commit. Responses do not reveal SQL or exception details.

Frontend and automatic Swagger CSRF handling remain outside this increment; full release acceptance remains pending.

## Withdraw with safe retries

Select an owned account UUID from GET /api/accounts. With the current session cookie and X-CSRF-TOKEN, call POST /api/transactions/withdraw with exactly one Idempotency-Key (for example a new UUID):

```json
{"accountId":"<your-account-uuid>","amount":200000}
```

Only accountId and amount are accepted. No OTP, description, bank details or preview is required. Amount must be a positive integral JSON number within the same VND limit as deposits; strings and fractions fail with 400. The locked account must cover the amount; withdrawing its entire balance is valid, insufficient funds returns 422 INSUFFICIENT_FUNDS. A new withdrawal returns 201 with Location and one SUCCESS/WITHDRAWAL transaction. Source UUID/number identify the account; destination UUID/number and description are null. Follow Location or retrieve the owned account's history to inspect it.

Retry an uncertain result with the same key AND body. A completed equivalent request (including 200000.0 instead of 200000) returns 200 with the original ID/time/result, even if the account is now empty. A completed key with another owned account or amount returns 409 IDEMPOTENCY_KEY_REUSED. An unowned account returns 403 before any replay lookup. Invalid/missing/repeated headers return 400. Keys follow the transfer ASCII/case/length rules above; TRANSFER and WITHDRAWAL have independent namespaces, so the same string can be used once in each. Within an operation keys are global across accounts/users. Successful keys persist indefinitely and survive restart; log in again and refresh CSRF before replay. Failures retain neither debit, transaction nor key. The server never automatically retries financial writes; a 409 CONCURRENCY_CONFLICT can be retried with the same key/body.

Swagger walkthrough: log in and refresh CSRF, deposit funds, withdraw with a new key (201), repeat unchanged (200, same ID), change amount with that key (409), inspect balance/Location/history, then try an overdraw with a fresh key (422). This increment retains manual X-CSRF-TOKEN input. Automated real HTTP session/restart walkthrough is in AuthRestartTest; browser UI acceptance is tracked separately.
