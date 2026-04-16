package com.zhiwiki.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiwiki.auth.dto.LoginResponse;
import com.zhiwiki.auth.entity.User;
import com.zhiwiki.auth.mapper.UserMapper;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * OAuth2/SSO单点登录服务
 * 
 * 支持的OAuth2提供商：
 * 1. 企业微信 (WeCom)
 * 2. 钉钉 (DingTalk)
 * 3. 通用OAuth2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2Service {

    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${oauth2.wecom.corp-id:}")
    private String wecomCorpId;
    @Value("${oauth2.wecom.agent-id:}")
    private String wecomAgentId;
    @Value("${oauth2.wecom.secret:}")
    private String wecomSecret;
    @Value("${oauth2.wecom.redirect-uri:}")
    private String wecomRedirectUri;

    @Value("${oauth2.dingtalk.app-key:}")
    private String dingtalkAppKey;
    @Value("${oauth2.dingtalk.app-secret:}")
    private String dingtalkAppSecret;
    @Value("${oauth2.dingtalk.redirect-uri:}")
    private String dingtalkRedirectUri;

    @Value("${oauth2.generic.client-id:}")
    private String genericClientId;
    @Value("${oauth2.generic.client-secret:}")
    private String genericClientSecret;
    @Value("${oauth2.generic.auth-url:}")
    private String genericAuthUrl;
    @Value("${oauth2.generic.token-url:}")
    private String genericTokenUrl;
    @Value("${oauth2.generic.user-info-url:}")
    private String genericUserInfoUrl;
    @Value("${oauth2.generic.redirect-uri:}")
    private String genericRedirectUri;

    private static final String STATE_CACHE_PREFIX = "oauth2:state:";
    private static final long STATE_EXPIRE_SECONDS = 300;

    /**
     * 生成OAuth2授权URL
     */
    public String getAuthorizationUrl(String provider) {
        String state = IdUtil.fastSimpleUUID();
        // 缓存state防止CSRF
        redisTemplate.opsForValue().set(STATE_CACHE_PREFIX + state, provider, STATE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        return switch (provider.toLowerCase()) {
            case "wecom" -> buildWeComAuthUrl(state);
            case "dingtalk" -> buildDingTalkAuthUrl(state);
            case "generic" -> buildGenericAuthUrl(state);
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的OAuth2提供商: " + provider);
        };
    }

    /**
     * OAuth2回调处理
     */
    public LoginResponse handleCallback(String provider, String code, String state) {
        // 1. 验证state
        String cachedProvider = (String) redisTemplate.opsForValue().get(STATE_CACHE_PREFIX + state);
        if (cachedProvider == null || !cachedProvider.equals(provider)) {
            throw new BusinessException(ResultCode.AUTH_LOGIN_FAILED, "OAuth2 state验证失败");
        }
        redisTemplate.delete(STATE_CACHE_PREFIX + state);

        // 2. 根据提供商获取用户信息
        OAuth2UserInfo oauth2User = switch (provider.toLowerCase()) {
            case "wecom" -> handleWeComCallback(code);
            case "dingtalk" -> handleDingTalkCallback(code);
            case "generic" -> handleGenericCallback(code);
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的OAuth2提供商");
        };

        // 3. 查找或创建本地用户
        User user = findOrCreateUser(oauth2User);

        // 4. Sa-Token登录
        StpUtil.login(user.getUserId());
        String token = StpUtil.getTokenValue();

        log.info("OAuth2登录成功: provider={}, userId={}, username={}", provider, user.getUserId(), user.getUsername());

        return LoginResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .build();
    }

    /**
     * 绑定OAuth2账号到已有用户
     */
    public void bindOAuth2Account(String userId, String provider, String code) {
        OAuth2UserInfo oauth2User = switch (provider.toLowerCase()) {
            case "wecom" -> handleWeComCallback(code);
            case "dingtalk" -> handleDingTalkCallback(code);
            case "generic" -> handleGenericCallback(code);
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的OAuth2提供商");
        };

        // 将OAuth2 ID存入用户的扩展字段
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }

        String extInfo = String.format("{\"%sId\":\"%s\",\"%sName\":\"%s\"}",
                provider, oauth2User.oauth2Id, provider, oauth2User.nickname);
        user.setExtInfo(extInfo);
        userMapper.updateById(user);

        log.info("OAuth2账号绑定成功: userId={}, provider={}", userId, provider);
    }

    // ==================== 企业微信 ====================

    private String buildWeComAuthUrl(String state) {
        return String.format(
                "https://open.weixin.qq.com/connect/oauth2/authorize?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_base&state=%s#wechat_redirect",
                wecomCorpId, wecomRedirectUri, state);
    }

    private OAuth2UserInfo handleWeComCallback(String code) {
        try {
            // 获取access_token
            String tokenUrl = String.format(
                    "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s",
                    wecomCorpId, wecomSecret);
            String tokenResp = HttpUtil.get(tokenUrl);
            JsonNode tokenNode = objectMapper.readTree(tokenResp);
            String accessToken = tokenNode.path("access_token").asText();

            // 获取用户身份
            String userUrl = String.format(
                    "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token=%s&code=%s",
                    accessToken, code);
            String userResp = HttpUtil.get(userUrl);
            JsonNode userNode = objectMapper.readTree(userResp);
            String userId = userNode.path("userid").asText();

            // 获取用户详情
            String detailUrl = String.format(
                    "https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=%s&userid=%s",
                    accessToken, userId);
            String detailResp = HttpUtil.get(detailUrl);
            JsonNode detailNode = objectMapper.readTree(detailResp);

            return new OAuth2UserInfo(
                    "wecom_" + userId,
                    userId,
                    detailNode.path("name").asText(userId),
                    detailNode.path("email").asText(""),
                    detailNode.path("mobile").asText(""),
                    "wecom"
            );
        } catch (Exception e) {
            log.error("企业微信OAuth2回调处理失败", e);
            throw new BusinessException(ResultCode.AUTH_LOGIN_FAILED, "企业微信登录失败: " + e.getMessage());
        }
    }

    // ==================== 钉钉 ====================

    private String buildDingTalkAuthUrl(String state) {
        return String.format(
                "https://login.dingtalk.com/login/qrcode.htm?appid=%s&response_type=code&scope=openid&state=%s&redirect_uri=%s",
                dingtalkAppKey, state, dingtalkRedirectUri);
    }

    private OAuth2UserInfo handleDingTalkCallback(String code) {
        try {
            // 获取access_token
            String tokenUrl = "https://api.dingtalk.com/v1.0/oauth2/accessToken";
            String tokenBody = String.format("{\"appKey\":\"%s\",\"appSecret\":\"%s\"}", dingtalkAppKey, dingtalkAppSecret);
            String tokenResp = HttpUtil.post(tokenUrl, tokenBody);
            JsonNode tokenNode = objectMapper.readTree(tokenResp);
            String accessToken = tokenNode.path("accessToken").asText();

            // 获取用户信息
            String userUrl = "https://api.dingtalk.com/v1.0/contact/users/me";
            String userResp = HttpUtil.createPost(userUrl)
                    .header("x-acs-dingtalk-access-token", accessToken)
                    .body("{\"code\":\"" + code + "\"}")
                    .execute().body();
            JsonNode userNode = objectMapper.readTree(userResp);

            String openId = userNode.path("openId").asText();
            String nick = userNode.path("nick").asText(openId);
            String email = userNode.path("email").asText("");
            String mobile = userNode.path("mobile").asText("");

            return new OAuth2UserInfo(
                    "dingtalk_" + openId,
                    openId,
                    nick,
                    email,
                    mobile,
                    "dingtalk"
            );
        } catch (Exception e) {
            log.error("钉钉OAuth2回调处理失败", e);
            throw new BusinessException(ResultCode.AUTH_LOGIN_FAILED, "钉钉登录失败: " + e.getMessage());
        }
    }

    // ==================== 通用OAuth2 ====================

    private String buildGenericAuthUrl(String state) {
        return String.format("%s?client_id=%s&redirect_uri=%s&response_type=code&scope=openid+profile+email&state=%s",
                genericAuthUrl, genericClientId, genericRedirectUri, state);
    }

    private OAuth2UserInfo handleGenericCallback(String code) {
        try {
            // 交换token
            String tokenBody = String.format(
                    "{\"grant_type\":\"authorization_code\",\"code\":\"%s\",\"redirect_uri\":\"%s\",\"client_id\":\"%s\",\"client_secret\":\"%s\"}",
                    code, genericRedirectUri, genericClientId, genericClientSecret);
            String tokenResp = HttpUtil.post(genericTokenUrl, tokenBody);
            JsonNode tokenNode = objectMapper.readTree(tokenResp);
            String accessToken = tokenNode.path("access_token").asText();

            // 获取用户信息
            String userResp = HttpUtil.createGet(genericUserInfoUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .execute().body();
            JsonNode userNode = objectMapper.readTree(userResp);

            String sub = userNode.path("sub").asText(userNode.path("id").asText());
            String name = userNode.path("name").asText(userNode.path("nickname").asText(sub));
            String email = userNode.path("email").asText("");
            String phone = userNode.path("phone_number").asText("");

            return new OAuth2UserInfo(
                    "generic_" + sub,
                    sub,
                    name,
                    email,
                    phone,
                    "generic"
            );
        } catch (Exception e) {
            log.error("通用OAuth2回调处理失败", e);
            throw new BusinessException(ResultCode.AUTH_LOGIN_FAILED, "OAuth2登录失败: " + e.getMessage());
        }
    }

    // ==================== 通用方法 ====================

    private User findOrCreateUser(OAuth2UserInfo oauth2User) {
        // 1. 通过ext_info字段查找已绑定的用户
        List<User> users = userMapper.selectList(null);
        for (User u : users) {
            if (u.getExtInfo() != null && u.getExtInfo().contains(oauth2User.oauth2Id)) {
                return u;
            }
        }

        // 2. 通过邮箱查找
        if (oauth2User.email != null && !oauth2User.email.isEmpty()) {
            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, oauth2User.email));
            if (user != null) {
                // 自动绑定
                user.setExtInfo(String.format("{\"%sId\":\"%s\"}", oauth2User.provider, oauth2User.oauth2Id));
                userMapper.updateById(user);
                return user;
            }
        }

        // 3. 通过手机号查找
        if (oauth2User.phone != null && !oauth2User.phone.isEmpty()) {
            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getPhone, oauth2User.phone));
            if (user != null) {
                user.setExtInfo(String.format("{\"%sId\":\"%s\"}", oauth2User.provider, oauth2User.oauth2Id));
                userMapper.updateById(user);
                return user;
            }
        }

        // 4. 自动创建新用户
        User newUser = new User();
        newUser.setUserId(IdUtil.fastSimpleUUID());
        newUser.setUsername(oauth2User.provider + "_" + oauth2User.providerId);
        newUser.setPassword(cn.dev33.satoken.secure.BCrypt.hashpw(IdUtil.fastSimpleUUID())); // 随机密码
        newUser.setRealName(oauth2User.nickname);
        newUser.setEmail(oauth2User.email);
        newUser.setPhone(oauth2User.phone);
        newUser.setSecurityLevel(1);
        newUser.setStatus(1);
        newUser.setIsDeleted(0);
        newUser.setExtInfo(String.format("{\"%sId\":\"%s\",\"%sName\":\"%s\",\"autoCreated\":true}",
                oauth2User.provider, oauth2User.oauth2Id, oauth2User.provider, oauth2User.nickname));
        userMapper.insert(newUser);

        log.info("OAuth2自动创建用户: userId={}, provider={}", newUser.getUserId(), oauth2User.provider);
        return newUser;
    }

    /**
     * OAuth2用户信息内部类
     */
    private record OAuth2UserInfo(
            String oauth2Id,
            String providerId,
            String nickname,
            String email,
            String phone,
            String provider
    ) {}
}
