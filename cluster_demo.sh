#!/usr/bin/env bash

sbt "runMain EShop.lab6.ProductCatalogWorkerNode seed-node1" &
sbt "runMain EShop.lab6.ProductCatalogWorkerNode seed-node2" &
sbt "runMain EShop.lab6.ProductCatalogWorkerNode seed-node3" &


sbt "runMain EShop.lab6.ProductCatalogServerNodeApp 1" &
sbt "runMain EShop.lab6.ProductCatalogServerNodeApp 2" &
sbt "runMain EShop.lab6.ProductCatalogServerNodeApp 3" &


# start gatling tests
#sbt gatling-it:test
#sbt gatling-it:lastReport