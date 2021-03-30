#!/usr/bin/env bash
set -ex

sbt \
-Dfork=true \
-Djp.co.tis.lerna.payment.application.persistence.cassandra.default.events-by-tag.first-time-bucket="$(date '+%Y%m%dT%H:%M' --utc)" \
-Dakka.cluster.min-nr-of-members=2 \
-Dakka.cluster.seed-nodes.0="akka://GatewaySystem@127.0.0.1:25520" \
-Dakka.cluster.seed-nodes.1="akka://GatewaySystem@127.0.0.2:25520" \
-Djp.co.tis.lerna.payment.server-mode=DEV \
-Dlerna.util.encryption.base64-key=v5LCFG4V1CbJxxPg+WTd8w== \
-Dlerna.util.encryption.base64-iv=46A7peszgqN3q/ww4k8lWg== \
-Djp.co.tis.lerna.payment.readmodel.rdbms.tenants.example.db.url=jdbc:mysql://127.0.0.1:3306/PAYMENTAPP \
-Djp.co.tis.lerna.payment.readmodel.rdbms.tenants.tenant-a.db.url=jdbc:mysql://127.0.0.2:3306/PAYMENTAPP \
-Djp.co.tis.lerna.payment.readmodel.rdbms.default.db.user=paymentapp \
-Djp.co.tis.lerna.payment.readmodel.rdbms.default.db.password=password \
-Djp.co.tis.lerna.payment.gateway.issuing.default.base-url="http://127.0.0.1:8083" \
-Djp.co.tis.lerna.payment.gateway.wallet-system.default.base-url="http://127.0.0.1:8083" \
-Dkamon.system-metrics.host.sigar-native-folder=native/2 \
-Dakka.remote.artery.canonical.hostname="127.0.0.2" \
-Dpublic-internet.http.interface="127.0.0.2" \
-Dprivate-internet.http.interface="127.0.0.2" \
-Dmanagement.http.interface="127.0.0.2" \
entrypoint/run
