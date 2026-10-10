plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("sensevoice_models")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
