# Contributions

**SCS 3311 — Smart Home Monitoring & Control System**

| Member | Index number | Module |
|---|---|---|
| R.L. Weerasinghe | 23002204 | Synchronisation, hardware simulator and safety worker |
| W.T. Mahagamage | 23001038 | Device profiles, control interface and usage reporting |
| D.M. Isakya | 23000643 | Floor representation, grid mapping and device placement |

## Module boundaries

The division follows the three headings the assignment requires the report to
cover, so that each member is responsible for one report section, one block of
the demonstration, and one topic in the oral defence.

### R.L. Weerasinghe (23002204) — synchronisation and safety

Realtime Database access layer (`app/src/main/java/.../data/remote/`), the
repository (`data/repository/`), the hardware simulator (`simulator/`), the
server-side safety worker (`worker/`), and the database security rules
(`database.rules.json`).

Responsible for the desired/reported/status separation, the child-listener
synchronisation model, the `max_on_duration` cut-off, and the derivation of the
four device states.

### W.T. Mahagamage (23001038) — device profiles and reporting

Device control interface (`app/src/main/java/.../ui/device/`), schedule and
safety configuration dialogs, usage reporting (`ui/report/`), the Room
persistence layer (`data/local/`), and CSV export.

Responsible for the three device profiles, the slot-level scheduling model, the
optimistic toggle and its reconciliation, and the aggregation queries behind the
reporting screen.

### D.M. Isakya (23000643) — floor representation

Floor management (`app/src/main/java/.../ui/floors/`), plan rendering and grid
overlay (`ui/plan/`), the coordinate mapper (`GridMapper.kt`), and device
placement.

Responsible for the abstract grid model, the cell-based coordinate system, the
aspect-ratio fitting of plans, and the visual encoding of device kind and status
on the plan.

## Shared work

The data contract in `docs/SCHEMA.md` was agreed by all three members before
implementation began, since it is the interface between the three modules and
between the Kotlin, TypeScript and JavaScript runtimes. Changes to it were made
jointly.

The technical report, demonstration script and defence notes were prepared
collaboratively, with each member drafting the section covering their own
module.

## Commit attribution

Commits should be made under each member's own Git identity for the work they
own. Where earlier commits were made from a shared machine, the module table
above is the authoritative record of responsibility.
