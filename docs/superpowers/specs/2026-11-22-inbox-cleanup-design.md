# One Inbox: Inbox Cleanup

Date: 2026-11-22 · Builds on: `2026-11-21-one-inbox-design.md` (which kept everything and deferred a "delete older than…" setting)

## Intent

The owner asked for "a cleanup for the inbox" and then said "go ahead", leaving the details to Claude. Today nothing is ever deleted: the list only grows, and the only tool is marking chats as read.

## Goals (Claude's calls)

1. **Swipe a chat away.** Swiping a row in the inbox, in either direction, deletes that conversation and its messages. A snackbar reads "Chat deleted" with **Undo**, which restores it exactly.
2. **A Cleanup card in Settings:**
   - **Auto-delete read chats:** Off / after 7 / 30 / 90 days. The default is **Off**, so nothing changes until the owner opts in. A chat is removed when it has no unread messages and its last message is older than the period. It runs when the inbox opens and when the setting changes.
   - **Clear read chats (N):** deletes every chat with no unread messages, after a confirm dialog ("can't be undone"). The button is disabled when N = 0.
3. **Deleted messages stay deleted.** Chat apps re-post the whole MessagingStyle history in every notification update. The app therefore keeps the dedupe keys of deleted messages in a `deleted_messages` table, and capture skips them. A new message in a deleted chat brings the chat back, holding only the new messages.

## Non-goals

- Deleting single messages, archiving, or muting.
- Deleting phone notifications. The app stays read-only toward other apps.

## Anti-goals

- **Unread messages lost silently.** Automatic and bulk cleanup never touch a chat with unread messages. Swiping removes unread messages too, but it is a deliberate gesture with Undo.
- **Deleted chats coming back from an old notification.**
- **The capture-health banner reporting deleted messages as "missed".** Tombstoned keys count as known.

## Approaches

| Approach | Trade-off | Verdict |
|---|---|---|
| A. Swipe with Undo + Settings cleanup + tombstones | Covers one-off and ongoing cleanup; needs a schema migration (v1→v2) | **Chosen** |
| B. Auto-delete by age only | No per-chat control | Rejected |
| C. Hide instead of delete (an archived flag) | Storage keeps growing; it isn't really "cleanup" | Rejected |

## Design notes (Claude's calls)

- **Room v2** adds `deleted_messages(dedupeKey TEXT PK, deletedAt INTEGER)`, with an explicit `Migration(1, 2)`. Nothing is destructive.
- Deleting writes the tombstones and removes the messages and the conversation in one transaction. The capture save filters tombstoned keys inside the same transaction.
- **Undo:** the deleted conversation and its messages are kept in memory. Undo re-inserts them with their original ids and removes their tombstones.
- Tombstones are never pruned. They are tiny, and pruning one could bring an old notification message back.
- A stored catch-up summary that mentions a deleted chat already shows a fallback title.
