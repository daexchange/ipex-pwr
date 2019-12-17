package ai.turbochain.ipex.wallet.entity;

import java.io.Serializable;
import java.math.BigDecimal;

import jnr.ffi.Struct.int16_t;


public class IPEXCoin implements Serializable{
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String name;
    /**
     * 中文
     */
    private String name_cn;
    /**
     * 缩写
     */
    private String unit;
    /**
     * 状态
     */
    private int status;
    /**
     * 最小提币手续费
     */
    private double min_tx_fee;
    /**
     * 对人民币汇率
     */
    private double cny_rate;
    /**
     * 最大提币手续费
     */
    private double max_tx_fee;
    /**
     * 对美元汇率
     */
    private double usd_rate;
    /**
     * 是否支持rpc接口
     */
    private int enable_rpc;
    /**
     * 排序
     */
    private int sort;
    /**
     * 是否能提币
     */
    private int can_withdraw;
    /**
     * 是否能充币
     */
    private int can_recharge;
    /**
     * 是否能转账
     */
    private int can_transfer;
    /**
     * 是否能自动提币
     */
    private int can_auto_withdraw;
    /**
     * 提币阈值
     */
    private BigDecimal withdraw_threshold;
    private BigDecimal min_withdraw_amount;
    private BigDecimal max_withdraw_amount;
    /**
     * 是否是平台币
     */
    private Integer is_platform_coin;
    /**
     * 是否是合法币种
     */
    private Boolean has_legal = false;
    private BigDecimal allBalance ;
    private String cold_wallet_address ;
    /**
     * 转账时付给矿工的手续费
     */
    private BigDecimal miner_fee;
    private int withdraw_scale;
    private int is_token;
    private String chain_name;
    private String token_address;
    private int decimals;
	public String getToken_address() {
		return token_address;
	}
	public void setToken_address(String token_address) {
		this.token_address = token_address;
	}
	public int getDecimals() {
		return decimals;
	}
	public void setDecimals(int decimals) {
		this.decimals = decimals;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getName_cn() {
		return name_cn;
	}
	public void setName_cn(String name_cn) {
		this.name_cn = name_cn;
	}
	public String getUnit() {
		return unit;
	}
	public void setUnit(String unit) {
		this.unit = unit;
	}
	public int getStatus() {
		return status;
	}
	public void setStatus(int status) {
		this.status = status;
	}
	public double getMin_tx_fee() {
		return min_tx_fee;
	}
	public void setMin_tx_fee(double min_tx_fee) {
		this.min_tx_fee = min_tx_fee;
	}
	public double getCny_rate() {
		return cny_rate;
	}
	public void setCny_rate(double cny_rate) {
		this.cny_rate = cny_rate;
	}
	public double getMax_tx_fee() {
		return max_tx_fee;
	}
	public void setMax_tx_fee(double max_tx_fee) {
		this.max_tx_fee = max_tx_fee;
	}
	public double getUsd_rate() {
		return usd_rate;
	}
	public void setUsd_rate(double usd_rate) {
		this.usd_rate = usd_rate;
	}
	public int getEnable_rpc() {
		return enable_rpc;
	}
	public void setEnable_rpc(int enable_rpc) {
		this.enable_rpc = enable_rpc;
	}
	public int getSort() {
		return sort;
	}
	public void setSort(int sort) {
		this.sort = sort;
	}
	public int getCan_withdraw() {
		return can_withdraw;
	}
	public void setCan_withdraw(int can_withdraw) {
		this.can_withdraw = can_withdraw;
	}
	public int getCan_recharge() {
		return can_recharge;
	}
	public void setCan_recharge(int can_recharge) {
		this.can_recharge = can_recharge;
	}
	public int getCan_transfer() {
		return can_transfer;
	}
	public void setCan_transfer(int can_transfer) {
		this.can_transfer = can_transfer;
	}
	public int getCan_auto_withdraw() {
		return can_auto_withdraw;
	}
	public void setCan_auto_withdraw(int can_auto_withdraw) {
		this.can_auto_withdraw = can_auto_withdraw;
	}
	public BigDecimal getWithdraw_threshold() {
		return withdraw_threshold;
	}
	public void setWithdraw_threshold(BigDecimal withdraw_threshold) {
		this.withdraw_threshold = withdraw_threshold;
	}
	public BigDecimal getMin_withdraw_amount() {
		return min_withdraw_amount;
	}
	public void setMin_withdraw_amount(BigDecimal min_withdraw_amount) {
		this.min_withdraw_amount = min_withdraw_amount;
	}
	public BigDecimal getMax_withdraw_amount() {
		return max_withdraw_amount;
	}
	public void setMax_withdraw_amount(BigDecimal max_withdraw_amount) {
		this.max_withdraw_amount = max_withdraw_amount;
	}
	public Integer getIs_platform_coin() {
		return is_platform_coin;
	}
	public void setIs_platform_coin(Integer is_platform_coin) {
		this.is_platform_coin = is_platform_coin;
	}
	public Boolean getHas_legal() {
		return has_legal;
	}
	public void setHas_legal(Boolean has_legal) {
		this.has_legal = has_legal;
	}
	public BigDecimal getAllBalance() {
		return allBalance;
	}
	public void setAllBalance(BigDecimal allBalance) {
		this.allBalance = allBalance;
	}
	public String getCold_wallet_address() {
		return cold_wallet_address;
	}
	public void setCold_wallet_address(String cold_wallet_address) {
		this.cold_wallet_address = cold_wallet_address;
	}
	public BigDecimal getMiner_fee() {
		return miner_fee;
	}
	public void setMiner_fee(BigDecimal miner_fee) {
		this.miner_fee = miner_fee;
	}
	public int getWithdraw_scale() {
		return withdraw_scale;
	}
	public void setWithdraw_scale(int withdraw_scale) {
		this.withdraw_scale = withdraw_scale;
	}
	public int getIs_token() {
		return is_token;
	}
	public void setIs_token(int is_token) {
		this.is_token = is_token;
	}
	public String getChain_name() {
		return chain_name;
	}
	public void setChain_name(String chain_name) {
		this.chain_name = chain_name;
	}
	public static long getSerialversionuid() {
		return serialVersionUID;
	}
}
