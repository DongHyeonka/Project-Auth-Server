# common AGENTS

Status:
- `common` is forbidden by default

Create `common` only if all are true:
- reused by 3+ modules
- not clearly owned by existing module
- does not break dependency direction
- duplication removal is worth a new shared dependency

Forbidden:
- dumping ground module
- shared dto mixing multiple layers
- shared business logic
- shared framework convenience code

Default:
- keep code in the owning layer
