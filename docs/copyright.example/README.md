# Software copyright registration (local only)

`docs/copyright/` is gitignored because it may contain personal identifiers (legal name, email, ID details).

## Setup

```bash
mkdir -p docs/copyright
cp docs/copyright.example/申请表填写参考.example.md docs/copyright/申请表填写参考.md
# Edit docs/copyright/申请表填写参考.md with your real information locally.
./scripts/generate-copyright-source.sh
```

Generate the software manual and source listing locally. Do not commit `docs/copyright/`.
