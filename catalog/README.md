# Migration specification catalog

该目录保存 `开源软件升级.xlsx` 的文档优先迁移规格，不保存工作簿本体。

- 工作簿 SHA-256：`17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309`
- Excel 数据行：4887
- 规格模块：1967
- 每个叶子模块固定包含 `README.md`、`migration.yaml` 和文档型 `pom.xml`。
- `catalog/pom.xml` 是非聚合 parent，不含 `<modules>`；catalog 不进入默认 Maven reactor。
- 规格、证据和自动化状态相互独立；README 完成不表示配方已经实现。

- `go`：265 个规格模块
- `java`：952 个规格模块
- `nodejs`：557 个规格模块
- `other`：70 个规格模块
- `python`：123 个规格模块

完整映射见 [`docs/workbook-module-index.md`](../docs/workbook-module-index.md)，
机器可读映射见 [`docs/workbook-module-index.csv`](../docs/workbook-module-index.csv)。
