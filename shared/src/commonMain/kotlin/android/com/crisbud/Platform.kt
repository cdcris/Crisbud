package android.com.crisbud

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform