/**
 * Browser-safe entry point (`@modeler/sql/lexers`) — the dialect **lexers** only,
 * for the Web Worker / Designer where the heavy parsers must not be bundled
 * (E11 bundle split; SPIKE S0.3: both lexers ~+155 KB gz, both parsers ~+839 KB
 * gz). The `.` entry (`index.ts`) adds the parsers + adapters for the desktop
 * (Node) host. `maskPlaceholders` (stage 2.2) is re-exported here too.
 */
export { TSqlLexer } from './generated/tsql/TSqlLexer.js';
export { PostgreSQLLexer } from './generated/postgresql/PostgreSQLLexer.js';
