@allure.label.owner:Tom @Zoom=40%
@LMEsmart @RIB @Reverse @UI @Regression
Feature: RIB trades reversal in LMEsmart
  State:
  9          - Pending Acceptance
  H          - Pending Other Side Acceptance

  @House @Inter-Office @Forward
  Scenario Outline: Successful reversal of buy side in RIB House inter-office trade in LMEsmart - Forward
    Given I logged in to LMEsmart as a RIB user
    When I submit the RIB trades details below
      | trade_date | time | template   | market | contract   | volume | traded_price   | traded_premium   | strike   | call_put   | volatility   | prompt_expiry   | is_strip_trade | underlying_price   | buyer_customer    | buyer_clearer | buyer_trader    | buyer_account | buyer_private_ref   | seller_customer   | seller_clearer | seller_trader   | seller_account | seller_private_ref   |
      | T+0        | T-1h | <template> | LME    | <contract> | 10     | <traded_price> | <traded_premium> | <strike> | <call_put> | <volatility> | <prompt_expiry> | No             | <underlying_price> | AUTO_Customer_a_0 | [Member CAT1] | AUTO_Trader_a_0 | H_1           | <buyer_private_ref> | AUTO_Customer_b_0 | [Member CAT2a] | AUTO_Trader_b_0 | H_1            | <seller_private_ref> |
    And I select 'Trade Management' in the menubar
    Then the trade should have the column value on the 'Rib Trade Management Page' as below
      | leg  | Trade Module Id       | State              | Trade State        |
      | Buy  | {Buy[TradeModuleId]}  | Pending Acceptance | Pending Acceptance |
      | Sell | {Sell[TradeModuleId]} | Pending Acceptance | Pending Acceptance |
    When I reverse the Buy trade in RIB Trade Management table
    Then the trade should have the column value on the 'Rib Trade Management Page' as below
      | leg          | Trade Module Id               | State              | Cancellation Flag | Cancel Link Id       | B Clearer      | S Clearer     |
      | Reverse_Buy  | {Reverse_Buy[TradeModuleId]}  | Pending Acceptance | R                 | {Buy[TradeModuleId]} | [Member CAT2a] | [Member CAT1] |
      | Reverse_Sell | {Reverse_Sell[TradeModuleId]} | Pending Acceptance | R                 | {Buy[TradeModuleId]} | [Member CAT2a] | [Member CAT1] |
    And the trade should be updated with values below by executing query 'get_trade_detail_audit_for_trade_leg_by_trade_half_id' from database 'LMEMATCHING'
      | leg          | TradeHalfId                 | State | ExecType | ClearingState |
      | Reverse_Buy  | {Reverse_Buy[TradeHalfId]}  | 9     | 9        | None          |
      | Reverse_Sell | {Reverse_Sell[TradeHalfId]} | 9     | 9        | None          |
    And the Reverse_Buy trade should have tags value below in the 'DiagnosticsLog' under the ‘TradeStatusFeed’ folder
      | 35 | 150 | 39 |
      | 8  | 9   | 9  |
    And the Reverse_Sell trade should have tags value below in the 'DiagnosticsLog' under the ‘TradeStatusFeed’ folder
      | 35 | 150 | 39 |
      | 8  | 9   | 9  |
    And the trades should be updated with the values below in the 'DiagnosticsLog' under the 'MatchingController' folder
      | leg          | status             |
      | Reverse_Buy  | PENDING_ACCEPTANCE |
      | Reverse_Sell | PENDING_ACCEPTANCE |
    @TC-539309
    Examples: Dollar
      | template | contract | traded_price | traded_premium | strike | call_put | volatility | prompt_expiry | underlying_price | buyer_private_ref                 | seller_private_ref                 |
      | F        | AAD      | db_price     |                |        |          |            | INS_M+2       |                  | [random5]-gpt-RIB-Reverse_Fwd-Buy | [random5]-gpt-RIB-Reverse_Fwd-Sell |
    @TC-543233
    Examples: Sterling
      | template | contract | traded_price | traded_premium | strike | call_put | volatility | prompt_expiry | underlying_price | buyer_private_ref                 | seller_private_ref                 |
      | F        | AAS      | db_price     |                |        |          |            | INS_M+2       |                  | [random5]-gpt-RIB-Reverse_Fwd-Buy | [random5]-gpt-RIB-Reverse_Fwd-Sell |
    @TC-543234
    Examples: Euro
      | template | contract | traded_price | traded_premium | strike | call_put | volatility | prompt_expiry | underlying_price | buyer_private_ref                 | seller_private_ref                 |
      | F        | AAE      | db_price     |                |        |          |            | INS_M+2       |                  | [random5]-gpt-RIB-Reverse_Fwd-Buy | [random5]-gpt-RIB-Reverse_Fwd-Sell |
    @TC-543235
    Examples: Yen
      | template | contract | traded_price | traded_premium | strike | call_put | volatility | prompt_expiry | underlying_price | buyer_private_ref                 | seller_private_ref                 |
      | F        | AAY      | db_price     |                |        |          |            | INS_M+2       |                  | [random5]-gpt-RIB-Reverse_Fwd-Buy | [random5]-gpt-RIB-Reverse_Fwd-Sell |
