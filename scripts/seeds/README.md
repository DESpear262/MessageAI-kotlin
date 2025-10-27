# Training Chats Dataset

This folder contains a multi-chat JSON dataset for exercising MessageAI's AI features (OPORD/WARNO/FRAGO generation, SITREP summarization, and CASEVAC workflow input extraction).

Files
- `training_chats.json`: Multiple 1:1 chats between a Lieutenant and various Captains/PSG, set in the D.A.T.E. Atropia/Donovia context.

Format
The loader accepts single or multiple chat formats. This dataset uses the multiple-chats format:

```json
{
  "chats": [
    {
      "users": ["sender@example.com", "receiver@example.com"],
      "chat": [
        { "from": "sender@example.com",   "contents": "Message text..." },
        { "from": "receiver@example.com", "contents": "Reply text..." }
      ]
    }
  ]
}
```

Usage
Run the loader with your dev service account credentials set via `GOOGLE_APPLICATION_CREDENTIALS`:

```bash
python scripts/load_chat.py scripts/chats/training_chats.json
```

Notes
- Loader resolves users by email in Firestore `users` collection. Create the user accounts first using the exact emails referenced in the dataset.
- All chats are 1:1; group chats are not required for this test plan.
- Message timestamps are server-generated at load time. In-text DTGs are for narrative realism only.
- Dataset intentionally mixes relevant and irrelevant details (roughly 40% relevant / 60% noise) and includes near-miss references (e.g., similar but different grids) to test extraction robustness.
