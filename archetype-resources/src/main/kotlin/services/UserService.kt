package ${package}.services

import ${package}.configurations.AppConfiguration
import ${package}.constants.AppConstants
import ${package}.helpers.PasswordHelper
import ${package}.helpers.RedisHelper
import ${package}.helpers.TokenHelper
import ${package}.helpers.WechatHelper
import ${package}.mappers.UserMapper
import ${package}.models.User
import ${package}.others.RedisKey
import ${package}.viewmodels.user.SignInResponse
import ${package}.viewmodels.user.UserEditRequest
import ${package}.viewmodels.user.UserSearchRequest
import ${package}.viewmodels.user.WechatEncryptedDataRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class UserService {
    @Autowired
    private lateinit var userMapper: UserMapper

    @Autowired
    private lateinit var tokenHelper: TokenHelper

    @Autowired
    private lateinit var passwordHelper: PasswordHelper

    @Autowired
    private lateinit var redisHelper: RedisHelper

    @Autowired
    private lateinit var wechatHelper: WechatHelper

    @Autowired
    private lateinit var appConfiguration: AppConfiguration

    fun editUser(request: UserEditRequest): Long {
        val user = User(
                id = request.id,
                mobile = request.mobile,
                openId = request.openId,
                nickname = request.nickname,
                username = request.username,
                password = request.password,
                salt = request.salt,
                avatarUrl = request.avatarUrl,
                role = request.role
        )
        return editUser(user)
    }

    fun editUser(user: User): Long {
        if (user.id == 0L) {
            userMapper.insertUser(user)
        } else {
            userMapper.updateUser(user)
        }
        return user.id
    }

    fun deleteUser(id: Long) {
        userMapper.deleteUser(id)
    }

    fun getUserById(id: Long): User? {
        return userMapper.selectUserById(id)
    }

    fun searchPagingUsers(request: UserSearchRequest): List<User> {
        return userMapper.selectPagingUsers(request)
    }

    fun searchPagingUsersCount(request: UserSearchRequest): Long {
        return userMapper.selectPagingUsersCount(request)
    }

    fun addUserWithPassword(request: UserEditRequest) {
        val salt = passwordHelper.salt
        var password = passwordHelper.salt
        if (request.password != null && request.password.isNotEmpty()) {
            password = passwordHelper.generate(request.password, salt)
        }
        val user = User(
                username = request.username,
                password = password,
                salt = salt,
                role = request.role
        )
        userMapper.insertUser(user)
    }

    fun editUserPassword(user: User, password: String) {
        val salt = passwordHelper.salt
        val encryptedPassword = passwordHelper.generate(password, salt)
        user.password = encryptedPassword
        user.salt = salt
        userMapper.updateUserPassword(user)
    }

    fun getUserByOpenId(openId: String): User? {
        return userMapper.selectUserByOpenId(openId)
    }

    fun getUserByUsername(username: String): User? {
        return userMapper.selectUserByUsername(username)
    }


    fun signIn(user: User): SignInResponse {
        val token = tokenHelper.createToken(AppConstants.Service.DEFAULT, user.id)
        return SignInResponse(
                username = user.username,
                token = token
        )
    }

    fun wxappSignIn(code: String): SignInResponse? {
        val sessionResult = wechatHelper.getSessionResultByCode(code, appConfiguration.wxAppId, appConfiguration.wxAppSecret)
        val user = getUserByOpenId(sessionResult.openId)
        if (user != null) {
            val token = tokenHelper.createToken(AppConstants.Service.DEFAULT, user.id)
            redisHelper.set(RedisKey.sessionKey(sessionResult.openId), sessionResult.sessionKey, 15, TimeUnit.MINUTES)

            val response = SignInResponse(token = token)
            response.nickname = user.nickname
            return response
        }
        return null
    }

    fun wxappRegister(request: WechatEncryptedDataRequest, openId: String) {
        val sessionKey = redisHelper.get(RedisKey.sessionKey(openId))!! // TODO: session_key 会过期
        val userInfo = wechatHelper.decryptUserInfo(sessionKey, request.encryptedData, request.iv)
        val user = User(
                nickname = userInfo.nickname,
                avatarUrl = userInfo.avatarUrl,
                openId = userInfo.openId
        )
        userMapper.insertUser(user)
    }

    fun signOut(user: User) {
        tokenHelper.deleteToken(AppConstants.Service.DEFAULT, user.id)
    }
}