package com.google.android.accessibility.selecttospeak;

/**
 * 这个类刻意使用了与 Android 系统内置"选择朗读"无障碍服务完全相同的
 * 包名和类名（com.google.android.accessibility.selecttospeak.SelectToSpeakService）。
 *
 * === 背景 ===
 * 微信从 8.0.52 版本左右开始，会对第三方无障碍服务读取到的输入框内容做混淆
 * 处理，导致读到的文字是假的乱码。但如果检测到发起读取请求的服务类名匹配
 * 系统内置读屏服务的白名单（比如 TalkBack、选择朗读），就不会混淆，会返回
 * 真实内容。这个判断依据只是"类名字符串是否匹配"，并不是更严格的应用签名
 * 校验，所以只要我们自己的服务也注册成一样的类名，就能让微信把真实内容给我们。
 *
 * === 来源 ===
 * 这是社区已经验证过的公开方案，开源库 ven-coder/Assists 里有现成实现，
 * 这里参考同样的思路。本质上是利用一个基于字符串匹配的白名单检查，不是
 * 官方支持的正规接口，微信未来版本随时可能改成更严格的校验方式（比如改
 * 成校验签名）导致这个方法失效，到时候需要重新想办法。
 *
 * === 为什么不用 TalkBack 这个类名 ===
 * 有人验证过用 TalkBack 的类名会导致部分机型（如小米）屏幕上一直悬浮
 * 显示提示文字，选择朗读（SelectToSpeak）没有这个副作用，所以选它。
 *
 * === 具体逻辑在哪里 ===
 * 所有实际的改写、无障碍事件处理、轮询兜底逻辑都写在 QQAccessibilityService
 * 里，这个类只是一个空壳子类，靠继承拿到全部功能，不需要写任何代码。
 */
public class SelectToSpeakService extends com.example.u7e5f3218e9.QQAccessibilityService {
}
