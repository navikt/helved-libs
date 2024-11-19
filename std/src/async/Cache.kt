package async

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface Cache<K, T> {
    suspend fun add(key: K, token: T)
    suspend fun get(key: K): T?
    suspend fun rm(key: K)
}

interface Token {
    fun isExpired(): Boolean
}

/**
 * Coroutine safe token cache
 */
class TokenCache<K, T: Token> : Cache<K, T> {
    private val tokens: HashMap<K, T> = hashMapOf()
    private val mutex = Mutex()

    override suspend fun add(key: K, token: T) {
        mutex.withLock {
            tokens[key] = token
        }
    }

    override suspend fun get(key: K): T? {
        mutex.withLock {
            tokens[key]
        }?.let {
            if (it.isExpired()) {
                rm(key)
            }
        }

        return mutex.withLock {
            tokens[key]
        }
    }

    override suspend fun rm(key: K) {
        mutex.withLock {
            tokens.remove(key)
        }
    }
}
