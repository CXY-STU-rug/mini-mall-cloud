package com.minimall.payment.enums;

/**
 * 支付渠道枚举。
 *
 * code 是落库用的数字 (payment.channel 字段是 tinyint), 枚举名是前端/代码里用的可读标识。
 * 两者的转换都收在本枚举里, 别处不再散落 "1==支付宝" 这种魔法数字。
 *
 * 新增渠道 (如微信) 只需在这里加一个枚举项 + 写一个对应的 PayChannelStrategy, 别处不用动。
 */
public enum PayChannel {

    /** 支付宝, 落库 channel=1 */
    ALIPAY((byte) 1);
    // 预留: WECHAT((byte) 2), BALANCE((byte) 3)

    /** 落库用的渠道码 (存进 payment.channel) */
    private final byte code;

    PayChannel(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    /**
     * 按落库码反查枚举 (queryStatus 时从 payment.channel 读出码, 定位当初用的渠道)。
     * @throws IllegalArgumentException 库里存了未知码 (脏数据) 时抛, 及早暴露
     */
    public static PayChannel ofCode(Byte code) {
        if (code != null) {
            for (PayChannel channel : values()) {
                if (channel.code == code) {
                    return channel;
                }
            }
        }
        throw new IllegalArgumentException("未知支付渠道码: " + code);
    }

    /**
     * 按名字解析 (前端 CreatePayDTO.channel 传进来的字符串)。
     * 容错: null/空白默认支付宝, 兼容不传该字段的老前端; 大小写不敏感。
     * @throws IllegalArgumentException 传了不认识的渠道名
     */
    public static PayChannel ofName(String name) {
        if (name == null || name.isBlank()) {
            return ALIPAY;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的支付渠道: " + name);
        }
    }
}
