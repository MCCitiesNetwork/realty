package io.github.md5sha256.realty.localisation;

public final class MessageKeys {

    private MessageKeys() {}

    // error
    public static final String ERROR_NO_REGION = "error.no-region";

    // common
    public static final String COMMON_PLAYER_NOT_FOUND = "common.player-not-found";
    public static final String COMMON_PLAYERS_ONLY = "common.players-only";
    public static final String COMMON_NO_PERMISSION = "common.no-permission";
    public static final String COMMON_ERROR = "common.error";

    // accept-offer
    public static final String ACCEPT_OFFER_SUCCESS = "accept-offer.success";
    public static final String ACCEPT_OFFER_NO_OFFER = "accept-offer.no-offer";
    public static final String ACCEPT_OFFER_AUCTION_EXISTS = "accept-offer.auction-exists";
    public static final String ACCEPT_OFFER_ALREADY_ACCEPTED = "accept-offer.already-accepted";
    public static final String ACCEPT_OFFER_INSERT_FAILED = "accept-offer.insert-failed";
    public static final String ACCEPT_OFFER_NOT_SANCTIONED = "accept-offer.not-sanctioned";
    public static final String ACCEPT_OFFER_ERROR = "accept-offer.error";

    // add
    public static final String ADD_CHECK_PERMISSIONS_ERROR = "add.check-permissions-error";
    public static final String ADD_NO_PERMISSION = "add.no-permission";
    public static final String ADD_SUCCESS = "add.success";

    // agent-invite
    public static final String AGENT_INVITE_SUCCESS = "agent-invite.success";
    public static final String AGENT_INVITE_ALREADY_INVITED = "agent-invite.already-invited";
    public static final String AGENT_INVITE_ALREADY_AGENT = "agent-invite.already-agent";
    public static final String AGENT_INVITE_NOT_TITLEHOLDER = "agent-invite.not-titleholder";
    public static final String AGENT_INVITE_IS_TITLEHOLDER = "agent-invite.is-titleholder";
    public static final String AGENT_INVITE_IS_AUTHORITY = "agent-invite.is-authority";
    public static final String AGENT_INVITE_NO_FREEHOLD = "agent-invite.no-freehold";
    public static final String AGENT_INVITE_ERROR = "agent-invite.error";

    // agent-invite-accept
    public static final String AGENT_INVITE_ACCEPT_SUCCESS = "agent-invite-accept.success";
    public static final String AGENT_INVITE_ACCEPT_NOT_FOUND = "agent-invite-accept.not-found";
    public static final String AGENT_INVITE_ACCEPT_ALREADY_AGENT = "agent-invite-accept.already-agent";
    public static final String AGENT_INVITE_ACCEPT_ERROR = "agent-invite-accept.error";

    // agent-invite-reject
    public static final String AGENT_INVITE_REJECT_SUCCESS = "agent-invite-reject.success";
    public static final String AGENT_INVITE_REJECT_NOT_FOUND = "agent-invite-reject.not-found";
    public static final String AGENT_INVITE_REJECT_ERROR = "agent-invite-reject.error";

    // agent-invite-withdraw
    public static final String AGENT_INVITE_WITHDRAW_SUCCESS = "agent-invite-withdraw.success";
    public static final String AGENT_INVITE_WITHDRAW_NOT_FOUND = "agent-invite-withdraw.not-found";
    public static final String AGENT_INVITE_WITHDRAW_ERROR = "agent-invite-withdraw.error";

    // agent-invite notifications
    public static final String NOTIFICATION_AGENT_INVITED = "notification.agent-invited";
    public static final String NOTIFICATION_AGENT_INVITE_ACCEPTED = "notification.agent-invite-accepted";
    public static final String NOTIFICATION_AGENT_INVITE_REJECTED = "notification.agent-invite-rejected";
    public static final String NOTIFICATION_AGENT_INVITE_WITHDRAWN = "notification.agent-invite-withdrawn";
    public static final String NOTIFICATION_AGENT_REMOVED = "notification.agent-removed";

    // agent-remove
    public static final String AGENT_REMOVE_SUCCESS = "agent-remove.success";
    public static final String AGENT_REMOVE_NOT_FOUND = "agent-remove.not-found";
    public static final String AGENT_REMOVE_ERROR = "agent-remove.error";

    // auction
    public static final String AUCTION_SUCCESS = "auction.success";
    public static final String AUCTION_NOT_SANCTIONED = "auction.not-sanctioned";
    public static final String AUCTION_NO_FREEHOLD_CONTRACT = "auction.no-freehold-contract";
    public static final String AUCTION_ERROR = "auction.error";

    // auction-info
    public static final String AUCTION_INFO_HEADER = "auction-info.header";
    public static final String AUCTION_INFO_NO_AUCTION = "auction-info.no-auction";
    public static final String AUCTION_INFO_DETAILS = "auction-info.details";
    public static final String AUCTION_INFO_ERROR = "auction-info.error";

    // bid
    public static final String BID_NO_AUCTION = "bid.no-auction";
    public static final String BID_IS_OWNER = "bid.is-owner";
    public static final String BID_TOO_LOW_MINIMUM = "bid.too-low-minimum";
    public static final String BID_TOO_LOW_CURRENT = "bid.too-low-current";
    public static final String BID_ALREADY_HIGHEST = "bid.already-highest";
    public static final String BID_SUCCESS = "bid.success";
    public static final String BID_ERROR = "bid.error";

    // buy
    public static final String BUY_ERROR = "buy.error";
    public static final String BUY_NO_FREEHOLD_CONTRACT = "buy.no-freehold-contract";
    public static final String BUY_NOT_FOR_SALE = "buy.not-for-sale";
    public static final String BUY_IS_AUTHORITY = "buy.is-authority";
    public static final String BUY_IS_TITLE_HOLDER = "buy.is-title-holder";
    public static final String BUY_INSUFFICIENT_FUNDS = "buy.insufficient-funds";
    public static final String BUY_PAYMENT_FAILED = "buy.payment-failed";
    public static final String BUY_SUCCESS = "buy.success";
    public static final String BUY_TRANSFER_FAILED = "buy.transfer-failed";

    // cancel-auction
    public static final String CANCEL_AUCTION_NO_AUCTION = "cancel-auction.no-auction";
    public static final String CANCEL_AUCTION_SUCCESS = "cancel-auction.success";
    public static final String CANCEL_AUCTION_ERROR = "cancel-auction.error";

    // create
    public static final String CREATE_LEASE_SUCCESS = "create.lease-success";
    public static final String CREATE_FREEHOLD_SUCCESS = "create.freehold-success";
    public static final String CREATE_ALREADY_REGISTERED = "create.already-registered";
    public static final String CREATE_REGION_EXISTS = "create.region-exists";
    public static final String CREATE_INCOMPLETE_SELECTION = "create.incomplete-selection";
    public static final String CREATE_ERROR = "create.error";

    // register-freehold
    public static final String REGISTER_FREEHOLD_SUCCESS = "register-freehold.success";
    public static final String REGISTER_FREEHOLD_ALREADY_REGISTERED = "register-freehold.already-registered";
    public static final String REGISTER_FREEHOLD_ERROR = "register-freehold.error";

    // register-rental
    public static final String REGISTER_RENTAL_SUCCESS = "register-rental.success";
    public static final String REGISTER_RENTAL_ALREADY_REGISTERED = "register-rental.already-registered";
    public static final String REGISTER_RENTAL_ERROR = "register-rental.error";

    // delete
    public static final String DELETE_NOT_REGISTERED = "delete.not-registered";
    public static final String DELETE_WORLDGUARD_SAVE_ERROR = "delete.worldguard-save-error";
    public static final String DELETE_SUCCESS = "delete.success";
    public static final String DELETE_ERROR = "delete.error";

    // help
    public static final String HELP_MAIN = "help.main";
    public static final String HELP_UNKNOWN_CATEGORY = "help.unknown-category";
    public static final String HELP_BASICS = "help.basics";
    public static final String HELP_MANAGEMENT = "help.management";
    public static final String HELP_OFFERS = "help.offers";
    public static final String HELP_AUCTIONS = "help.auctions";
    public static final String HELP_ADMIN = "help.admin";

    // history
    public static final String HISTORY_HEADER = "history.header";
    public static final String HISTORY_NO_RESULTS = "history.no-results";
    public static final String HISTORY_FOOTER = "history.footer";
    public static final String HISTORY_PREVIOUS = "history.previous";
    public static final String HISTORY_NEXT = "history.next";
    public static final String HISTORY_INVALID_PAGE = "history.invalid-page";
    public static final String HISTORY_ERROR = "history.error";

    // history per-event-type entry messages
    public static final String HISTORY_EVENT_BUY = "history.event.buy";
    public static final String HISTORY_EVENT_AUCTION_BUY = "history.event.auction-buy";
    public static final String HISTORY_EVENT_OFFER_BUY = "history.event.offer-buy";
    public static final String HISTORY_EVENT_AGENT_ADD = "history.event.agent-add";
    public static final String HISTORY_EVENT_AGENT_REMOVE = "history.event.agent-remove";
    public static final String HISTORY_EVENT_RENT = "history.event.rent";
    public static final String HISTORY_EVENT_UNRENT = "history.event.unrent";
    public static final String HISTORY_EVENT_RENEW = "history.event.renew";
    public static final String HISTORY_EVENT_LEASE_EXPIRY = "history.event.lease-expiry";

    // info
    public static final String INFO_HEADER = "info.header";
    public static final String INFO_NO_CONTRACTS = "info.no-contracts";
    public static final String INFO_SOLD = "info.sold";
    public static final String INFO_FOR_SALE = "info.for-sale";
    public static final String INFO_LEASE = "info.lease";
    public static final String INFO_AUCTION_ACTIVE = "info.auction-active";
    public static final String INFO_ERROR = "info.error";

    // list
    public static final String LIST_PLAYERS_ONLY = "list.players-only";
    public static final String LIST_NO_REGIONS = "list.no-regions";
    public static final String LIST_INVALID_PAGE = "list.invalid-page";
    public static final String LIST_HEADER = "list.header";
    public static final String LIST_CATEGORY = "list.category";
    public static final String LIST_ENTRY = "list.entry";
    public static final String LIST_FOOTER = "list.footer";
    public static final String LIST_PREVIOUS = "list.previous";
    public static final String LIST_NEXT = "list.next";
    public static final String LIST_ERROR = "list.error";

    // offer
    public static final String OFFER_SUCCESS = "offer.success";
    public static final String OFFER_NO_FREEHOLD_CONTRACT = "offer.no-freehold-contract";
    public static final String OFFER_IS_OWNER = "offer.is-owner";
    public static final String OFFER_ALREADY_HAS_OFFER = "offer.already-has-offer";
    public static final String OFFER_AUCTION_EXISTS = "offer.auction-exists";
    public static final String OFFER_INSERT_FAILED = "offer.insert-failed";
    public static final String OFFER_ERROR = "offer.error";

    // offers-inbound
    public static final String OFFERS_INBOUND_NO_OFFERS = "offers-inbound.no-offers";
    public static final String OFFERS_INBOUND_HEADER = "offers-inbound.header";
    public static final String OFFERS_INBOUND_ENTRY = "offers-inbound.entry";
    public static final String OFFERS_INBOUND_ERROR = "offers-inbound.error";

    // offers-list
    public static final String OFFERS_LIST_NO_OFFERS = "offers-list.no-offers";
    public static final String OFFERS_LIST_HEADER = "offers-list.header";
    public static final String OFFERS_LIST_ENTRY = "offers-list.entry";
    public static final String OFFERS_LIST_ERROR = "offers-list.error";

    // pay-bid
    public static final String PAY_BID_INSUFFICIENT_FUNDS = "pay-bid.insufficient-funds";
    public static final String PAY_BID_PAYMENT_FAILED = "pay-bid.payment-failed";
    public static final String PAY_BID_SUCCESS = "pay-bid.success";
    public static final String PAY_BID_FULLY_PAID = "pay-bid.fully-paid";
    public static final String PAY_BID_NO_PAYMENT_RECORD = "pay-bid.no-payment-record";
    public static final String PAY_BID_PAYMENT_EXPIRED = "pay-bid.payment-expired";
    public static final String PAY_BID_EXCEEDS_OWED = "pay-bid.exceeds-owed";
    public static final String PAY_BID_ERROR = "pay-bid.error";
    public static final String PAY_BID_TRANSFER_FAILED = "pay-bid.transfer-failed";
    public static final String PAY_BID_TRANSFER_SUCCESS = "pay-bid.transfer-success";

    // pay-offer
    public static final String PAY_OFFER_INSUFFICIENT_FUNDS = "pay-offer.insufficient-funds";
    public static final String PAY_OFFER_PAYMENT_FAILED = "pay-offer.payment-failed";
    public static final String PAY_OFFER_SUCCESS = "pay-offer.success";
    public static final String PAY_OFFER_FULLY_PAID = "pay-offer.fully-paid";
    public static final String PAY_OFFER_NO_PAYMENT_RECORD = "pay-offer.no-payment-record";
    public static final String PAY_OFFER_EXCEEDS_OWED = "pay-offer.exceeds-owed";
    public static final String PAY_OFFER_ERROR = "pay-offer.error";
    public static final String PAY_OFFER_TRANSFER_FAILED = "pay-offer.transfer-failed";
    public static final String PAY_OFFER_TRANSFER_SUCCESS = "pay-offer.transfer-success";

    // reject-offer
    public static final String REJECT_OFFER_SUCCESS = "reject-offer.success";
    public static final String REJECT_OFFER_ALL_SUCCESS = "reject-offer.all-success";
    public static final String REJECT_OFFER_NO_OFFER = "reject-offer.no-offer";
    public static final String REJECT_OFFER_NOT_SANCTIONED = "reject-offer.not-sanctioned";
    public static final String REJECT_OFFER_NO_FREEHOLD_CONTRACT = "reject-offer.no-freehold-contract";
    public static final String REJECT_OFFER_ACCEPTED = "reject-offer.accepted";
    public static final String REJECT_OFFER_ERROR = "reject-offer.error";

    // reload
    public static final String RELOAD_SUCCESS = "reload.success";
    public static final String RELOAD_ERROR = "reload.error";

    // remove
    public static final String REMOVE_CHECK_PERMISSIONS_ERROR = "remove.check-permissions-error";
    public static final String REMOVE_NO_PERMISSION = "remove.no-permission";
    public static final String REMOVE_SUCCESS = "remove.success";

    // renew
    public static final String RENEW_SUCCESS = "renew.success";
    public static final String RENEW_NO_LEASE_CONTRACT = "renew.no-lease-contract";
    public static final String RENEW_NOT_TENANT = "renew.not-tenant";
    public static final String RENEW_NO_EXTENSIONS = "renew.no-extensions";
    public static final String RENEW_UPDATE_FAILED = "renew.update-failed";
    public static final String RENEW_INSUFFICIENT_FUNDS = "renew.insufficient-funds";
    public static final String RENEW_PAYMENT_FAILED = "renew.payment-failed";
    public static final String RENEW_ERROR = "renew.error";

    // unrent
    public static final String UNRENT_SUCCESS = "unrent.success";
    public static final String UNRENT_NO_LEASE_CONTRACT = "unrent.no-lease-contract";
    public static final String UNRENT_NOT_TENANT = "unrent.not-tenant";
    public static final String UNRENT_UPDATE_FAILED = "unrent.update-failed";
    public static final String UNRENT_REFUND_FAILED = "unrent.refund-failed";
    public static final String UNRENT_ERROR = "unrent.error";

    // rent
    public static final String RENT_SUCCESS = "rent.success";
    public static final String RENT_NO_LEASE_CONTRACT = "rent.no-lease-contract";
    public static final String RENT_IS_LANDLORD = "rent.is-landlord";
    public static final String RENT_ALREADY_OCCUPIED = "rent.already-occupied";
    public static final String RENT_UPDATE_FAILED = "rent.update-failed";
    public static final String RENT_INSUFFICIENT_FUNDS = "rent.insufficient-funds";
    public static final String RENT_PAYMENT_FAILED = "rent.payment-failed";
    public static final String RENT_ERROR = "rent.error";

    // set (shared)
    public static final String SET_NO_PERMISSION = "set.no-permission";
    public static final String SET_CHECK_PERMISSIONS_ERROR = "set.check-permissions-error";

    // set-duration
    public static final String SET_DURATION_SUCCESS = "set-duration.success";
    public static final String SET_DURATION_NO_LEASE_CONTRACT = "set-duration.no-lease-contract";
    public static final String SET_DURATION_UPDATE_FAILED = "set-duration.update-failed";
    public static final String SET_DURATION_ERROR = "set-duration.error";

    // set-maxrenewals
    public static final String SET_MAX_RENEWALS_SUCCESS = "set-maxrenewals.success";
    public static final String SET_MAX_RENEWALS_NO_LEASE_CONTRACT = "set-maxrenewals.no-lease-contract";
    public static final String SET_MAX_RENEWALS_BELOW_CURRENT = "set-maxrenewals.below-current";
    public static final String SET_MAX_RENEWALS_UPDATE_FAILED = "set-maxrenewals.update-failed";
    public static final String SET_MAX_RENEWALS_ERROR = "set-maxrenewals.error";

    // set-landlord
    public static final String SET_LANDLORD_SUCCESS = "set-landlord.success";
    public static final String SET_LANDLORD_NO_LEASE_CONTRACT = "set-landlord.no-lease-contract";
    public static final String SET_LANDLORD_UPDATE_FAILED = "set-landlord.update-failed";
    public static final String SET_LANDLORD_ERROR = "set-landlord.error";

    // set-price
    public static final String SET_PRICE_SUCCESS = "set-price.success";
    public static final String SET_PRICE_NO_FREEHOLD_CONTRACT = "set-price.no-freehold-contract";
    public static final String SET_PRICE_AUCTION_EXISTS = "set-price.auction-exists";
    public static final String SET_PRICE_OFFER_PAYMENT_IN_PROGRESS = "set-price.offer-payment-in-progress";
    public static final String SET_PRICE_BID_PAYMENT_IN_PROGRESS = "set-price.bid-payment-in-progress";
    public static final String SET_PRICE_UPDATE_FAILED = "set-price.update-failed";
    public static final String SET_PRICE_ERROR = "set-price.error";

    // set-tenant
    public static final String SET_TENANT_SUCCESS = "set-tenant.success";
    public static final String SET_TENANT_NO_LEASE_CONTRACT = "set-tenant.no-lease-contract";
    public static final String SET_TENANT_UPDATE_FAILED = "set-tenant.update-failed";
    public static final String SET_TENANT_ERROR = "set-tenant.error";

    // set-titleholder
    public static final String SET_TITLEHOLDER_SUCCESS = "set-titleholder.success";
    public static final String SET_TITLEHOLDER_NO_FREEHOLD_CONTRACT = "set-titleholder.no-freehold-contract";
    public static final String SET_TITLEHOLDER_UPDATE_FAILED = "set-titleholder.update-failed";
    public static final String SET_TITLEHOLDER_ERROR = "set-titleholder.error";

    // unset (shared)
    public static final String UNSET_NO_PERMISSION = "unset.no-permission";
    public static final String UNSET_CHECK_PERMISSIONS_ERROR = "unset.check-permissions-error";

    // unset-price
    public static final String UNSET_PRICE_SUCCESS = "unset-price.success";
    public static final String UNSET_PRICE_NO_FREEHOLD_CONTRACT = "unset-price.no-freehold-contract";
    public static final String UNSET_PRICE_OFFER_PAYMENT_IN_PROGRESS = "unset-price.offer-payment-in-progress";
    public static final String UNSET_PRICE_BID_PAYMENT_IN_PROGRESS = "unset-price.bid-payment-in-progress";
    public static final String UNSET_PRICE_UPDATE_FAILED = "unset-price.update-failed";
    public static final String UNSET_PRICE_ERROR = "unset-price.error";

    // unset-tenant
    public static final String UNSET_TENANT_SUCCESS = "unset-tenant.success";
    public static final String UNSET_TENANT_NO_LEASE_CONTRACT = "unset-tenant.no-lease-contract";
    public static final String UNSET_TENANT_UPDATE_FAILED = "unset-tenant.update-failed";
    public static final String UNSET_TENANT_ERROR = "unset-tenant.error";

    // unset-titleholder
    public static final String UNSET_TITLEHOLDER_SUCCESS = "unset-titleholder.success";
    public static final String UNSET_TITLEHOLDER_NO_FREEHOLD_CONTRACT = "unset-titleholder.no-freehold-contract";
    public static final String UNSET_TITLEHOLDER_UPDATE_FAILED = "unset-titleholder.update-failed";
    public static final String UNSET_TITLEHOLDER_ERROR = "unset-titleholder.error";

    // withdraw-offer
    public static final String WITHDRAW_OFFER_NO_OFFER = "withdraw-offer.no-offer";
    public static final String WITHDRAW_OFFER_ACCEPTED = "withdraw-offer.accepted";
    public static final String WITHDRAW_OFFER_SUCCESS = "withdraw-offer.success";
    public static final String WITHDRAW_OFFER_ERROR = "withdraw-offer.error";

    // subregion
    public static final String SUBREGION_CREATE_SUCCESS = "subregion.create-success";
    public static final String SUBREGION_CREATE_ERROR = "subregion.create-error";
    public static final String SUBREGION_REGION_EXISTS = "subregion.region-exists";
    public static final String SUBREGION_NO_FREEHOLD = "subregion.no-freehold";
    public static final String SUBREGION_NOT_TITLEHOLDER = "subregion.not-titleholder";
    public static final String SUBREGION_WRONG_WORLD = "subregion.wrong-world";
    public static final String SUBREGION_INCOMPLETE_SELECTION = "subregion.incomplete-selection";
    public static final String SUBREGION_EXCEEDS_BOUNDS = "subregion.exceeds-bounds";
    public static final String SUBREGION_OVERLAPS_SIBLING = "subregion.overlaps-sibling";
    public static final String SUBREGION_TOO_SMALL = "subregion.too-small";

    // teleport
    public static final String TP_SUCCESS = "tp.success";
    public static final String TP_NO_SAFE_LOCATION = "tp.no-safe-location";
    public static final String TP_ERROR = "tp.error";

    // sign
    public static final String SIGN_PLACE_SUCCESS = "sign.place-success";
    public static final String SIGN_PLACE_NOT_A_SIGN = "sign.place-not-a-sign";
    public static final String SIGN_PLACE_NOT_REGISTERED = "sign.place-not-registered";
    public static final String SIGN_PLACE_ALREADY_REGISTERED = "sign.place-already-registered";
    public static final String SIGN_PLACE_ERROR = "sign.place-error";
    public static final String SIGN_REMOVE_SUCCESS = "sign.remove-success";
    public static final String SIGN_REMOVE_NOT_A_SIGN = "sign.remove-not-a-sign";
    public static final String SIGN_REMOVE_NOT_REGISTERED = "sign.remove-not-registered";
    public static final String SIGN_REMOVE_ERROR = "sign.remove-error";

    // notification
    public static final String NOTIFICATION_OFFER_PLACED = "notification.offer-placed";
    public static final String NOTIFICATION_OFFER_ACCEPTED = "notification.offer-accepted";
    public static final String NOTIFICATION_OFFER_REJECTED = "notification.offer-rejected";
    public static final String NOTIFICATION_OFFER_WITHDRAWN = "notification.offer-withdrawn";
    public static final String NOTIFICATION_OWNERSHIP_TRANSFERRED = "notification.ownership-transferred";
    public static final String NOTIFICATION_OUTBID = "notification.outbid";
    public static final String NOTIFICATION_AUCTION_CANCELLED = "notification.auction-cancelled";
    public static final String NOTIFICATION_AUCTION_WON = "notification.auction-won";
    public static final String NOTIFICATION_AUCTION_ENDED_NO_BIDS = "notification.auction-ended-no-bids";
    public static final String NOTIFICATION_REGION_BOUGHT = "notification.region-bought";
    public static final String NOTIFICATION_REGION_RENTED = "notification.region-rented";
    public static final String NOTIFICATION_BID_PAYMENT_EXPIRED = "notification.bid-payment-expired";
    public static final String NOTIFICATION_OFFER_PAYMENT_EXPIRED = "notification.offer-payment-expired";
    public static final String NOTIFICATION_LEASE_EXPIRED = "notification.lease-expired";
    public static final String NOTIFICATION_LEASE_EXPIRED_LANDLORD = "notification.lease-expired-landlord";
    public static final String NOTIFICATION_REGION_UNRENTED = "notification.region-unrented";
}
