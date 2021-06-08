package updated.mysterium.vpn.database.preferences

enum class SharedPreferencesList(val prefName: String) {
    //Logic parameters
    BALANCE("BALANCE"),
    MIN_BALANCE("MIN_BALANCE"),
    BALANCE_PUSH("BALANCE_PUSH"),
    MIN_BALANCE_PUSH("MIN_BALANCE_PUSH"),
    LOGIN("LOGIN"),
    TOP_UP_FLOW("TOP_UP_FLOW"),
    ACCOUNT_CREATED("ACCOUNT_CREATED"),
    TERMS("TERMS"),
    DNS("DNS"),
    IDENTITY_ADDRESS("IDENTITY_ADDRESS"),
    LANGUAGE("LANGUAGE"),
    CONNECTION_HINT("CONNECTION_HINT"),
    IS_NEW_USER("IS_NEW_USER"),
    DARK_MODE("DARK_MODE"),
    PREVIOUS_COUNTRY_CODE("PREVIOUS_COUNTRY_CODE"),
    PREVIOUS_FILTER_ID("PREVIOUS_FILTER_ID"),

    //Pushy parameters
    CRYPTO_PAYMENT("CRYPTO_PAYMENT"),
}
