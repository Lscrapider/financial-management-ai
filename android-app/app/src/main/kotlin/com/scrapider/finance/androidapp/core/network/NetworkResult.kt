package com.scrapider.finance.androidapp.core.network

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>

    data class Failure(val reason: NetworkFailure) : NetworkResult<Nothing>
}

enum class NetworkFailure(val userMessage: String) {
    Unavailable("暂时无法连接服务，请检查网络后重试。"),
    Unauthorized("登录状态已失效，请重新登录。"),
    Forbidden("当前账号没有访问该功能的权限。"),
    InvalidResponse("服务返回的数据无法解析，请稍后重试。"),
    Service("服务暂时不可用，请稍后重试。"),
}
