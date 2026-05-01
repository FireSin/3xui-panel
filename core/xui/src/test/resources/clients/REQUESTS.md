# 3x-ui client API — verified request shapes

Captured 2026-04-30 from a live 3x-ui panel (VLESS inbound). Cookies, hostname,
and path prefix have been stripped. UUIDs and inboundId are kept — they are
not secrets and serve as anchors for round-trip tests.

All three endpoints take **`Content-Type: application/x-www-form-urlencoded; charset=UTF-8`**.
The `addClient` / `updateClient` body has two form fields: `id` (inboundId, integer)
and `settings` (JSON-as-string, URL-encoded). `delClient` body is empty
(`Content-Length: 0`).

## addClient

```
POST /panel/api/inbounds/addClient
Content-Type: application/x-www-form-urlencoded; charset=UTF-8

id=<inboundId>&settings=<urlencoded JSON, see vless_add_settings.json>
```

## updateClient (VLESS — clientKey is the UUID)

```
POST /panel/api/inbounds/updateClient/<clientUuid>
Content-Type: application/x-www-form-urlencoded; charset=UTF-8

id=<inboundId>&settings=<urlencoded JSON, see vless_update_settings.json>
```

Note: on update, the JSON also carries `created_at` and `updated_at` (epoch ms).
`created_at` must be preserved from the existing client; `updated_at` is sent
by the client but the server may overwrite it.

## delClient

```
POST /panel/api/inbounds/<inboundId>/delClient/<clientUuid>
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
Content-Length: 0

(empty body)
```

## resetClientTraffic

Not yet captured live. Per upstream `web/service/inbound.go` the URL is:

```
POST /panel/api/inbounds/<inboundId>/resetClientTraffic/<email>
```

with empty body. Verify when first encountered live.
