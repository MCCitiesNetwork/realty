rootProject.name = "realty"

include("realty-backend-api")
include("realty-backend")
include("realty-paper-api")
include("realty-paper")
include("realty-web:realty-rest")
include("realty-web:realty-explorer")
include("realty-web:realty-web-dist")
// Excluded from the build: it does not compile against the current AreaShop
// dependency (ImportJob cannot access ReplacementProvider). The source is left in
// the tree; re-enable this line once that dependency is sorted out.
// include("realty-areashop-importer")
include("realty-paper-plan-extension")
include("realty-paper-adapters:chat-adapter")
include("realty-paper-adapters:essentials-adapter")
include("realty-paper-adapters:player-notifications-adapter")
include("realty-paper-adapters:query-service")
