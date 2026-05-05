package server.main.demo.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import server.main.demo.config.DemoTradeProperties;
import server.main.global.util.TickSizePolicy;
import server.main.member.entity.Member;
import server.main.member.entity.MemberTokenHolding;
import server.main.member.repository.AccountRepository;
import server.main.member.repository.MemberRepository;
import server.main.member.repository.MemberTokenHoldingRepository;
import server.main.myAccount.entity.Account;
import server.main.order.dto.OrderRequestDto;
import server.main.order.entity.OrderType;
import server.main.order.service.OrderFacade;
import server.main.token.entity.Token;
import server.main.token.repository.TokenRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoTradeServiceImpl implements DemoTradeService {

    private final DemoTradeProperties properties;
    private final TokenRepository tokenRepository;
    private final MemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final MemberTokenHoldingRepository memberTokenHoldingRepository;
    private final OrderFacade orderFacade;

    // 활성 회원과 거래 가능 토큰을 조회해 토큰별 데모 체결을 1건씩 생성
    @Override
    public void createDailyDemoTrades() {
        List<Member> activeMembers = memberRepository.findAllByIsActiveTrue();
        if (activeMembers.size() < 2) {
            log.warn("[DemoTrade] skipped: at least two active members are required");
            return;
        }

        List<Token> tokens = tokenRepository.findAllTradingTokensWithAsset();
        for (Token token : tokens) {
            try {
                createOneMatchedTrade(token, activeMembers);
            } catch (RuntimeException e) {
                log.warn("[DemoTrade] failed. tokenId={}, reason={}", token.getTokenId(), e.getMessage());
            }
        }
    }

    // 단일 토큰에 대해 판매자/구매자를 골라 같은 가격과 수량의 매도/매수 주문
    private void createOneMatchedTrade(Token token, List<Member> activeMembers) {
        long price = chooseOrderPrice(token);
        long quantity = chooseOrderQuantity(price);

        Member seller = findSeller(token, activeMembers, quantity)
                .orElseThrow(() -> new IllegalStateException("no demo seller holding enough token"));
        Member buyer = findBuyer(activeMembers, seller.getMemberId(), price, quantity)
                .orElseThrow(() -> new IllegalStateException("no demo buyer with enough balance"));

        createOrderAs(seller, token.getTokenId(), price, quantity, OrderType.SELL);
        createOrderAs(buyer, token.getTokenId(), price, quantity, OrderType.BUY);

        log.info(
                "[DemoTrade] matched order requested. tokenId={}, sellerId={}, buyerId={}, price={}, quantity={}",
                token.getTokenId(), seller.getMemberId(), buyer.getMemberId(), price, quantity
        );
    }

    // 현재가를 기준으로 지정한 틱 범위 안에서 호가 단위에 맞는 주문 가격을 선택
    private long chooseOrderPrice(Token token) {
        long basePrice = token.getCurrentPrice() != null ? token.getCurrentPrice() : token.getInitPrice();
        if (basePrice <= 0) {
            throw new IllegalStateException("token price is missing");
        }

        long tickSize = TickSizePolicy.getTickSize(basePrice);
        int tickOffset = ThreadLocalRandom.current()
                .nextInt(-properties.getPriceTickRange(), properties.getPriceTickRange() + 1);
        long price = Math.max(tickSize, basePrice + tickSize * tickOffset);
        long validTickSize = TickSizePolicy.getTickSize(price);
        return Math.max(validTickSize, (price / validTickSize) * validTickSize);
    }

    // 최소/최대 주문금액 범위에 들어오도록 가능한 주문 수량을 랜덤으로 선택
    private long chooseOrderQuantity(long price) {
        long minAmount = Math.max(1L, properties.getMinOrderAmount());
        long maxAmount = Math.max(minAmount, properties.getMaxOrderAmount());

        long minQuantity = Math.max(1L, roundUpDivide(minAmount, price));
        long maxQuantity = Math.max(minQuantity, maxAmount / price);
        return ThreadLocalRandom.current().nextLong(minQuantity, maxQuantity + 1);
    }

    // 정수 나눗셈에서 소수점을 올림 처리해 최소 주문금액 이상
    private long roundUpDivide(long dividend, long divisor) {
        return (dividend + divisor - 1) / divisor;
    }

    // 활성 회원 중 해당 토큰을 충분히 보유한 판매자를 랜덤으로
    private Optional<Member> findSeller(Token token, List<Member> activeMembers, long quantity) {
        List<Long> activeMemberIds = activeMembers.stream()
                .map(Member::getMemberId)
                .toList();
        List<MemberTokenHolding> holders = new ArrayList<>(
                memberTokenHoldingRepository.findHoldersByTokenId(token.getTokenId())
        );
        holders.removeIf(h -> !activeMemberIds.contains(h.getMember().getMemberId()));
        holders.removeIf(h -> h.getCurrentQuantity() == null || h.getCurrentQuantity() < quantity);
        Collections.shuffle(holders);

        return holders.stream()
                .map(MemberTokenHolding::getMember)
                .findFirst();
    }

    // 활성 회원 중 판매자를 제외하고 주문금액을 낼 수 있는 구매자를 랜덤
    private Optional<Member> findBuyer(List<Member> activeMembers, Long sellerId, long price, long quantity) {
        List<Member> candidates = new ArrayList<>(activeMembers);
        candidates.removeIf(member -> member.getMemberId().equals(sellerId));
        Collections.shuffle(candidates);

        long requiredBalance = Math.multiplyExact(price, quantity);
        for (Member member : candidates) {
            Optional<Account> account = accountRepository.findByMemberId(member.getMemberId());
            if (account.isPresent() && account.get().getAvailableBalance() >= requiredBalance) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }

    // 데모 내부 주문 경로를 통해 기존 주문/매치/체결 처리 흐름을 실행
    private void createOrderAs(Member member, Long tokenId, long price, long quantity, OrderType orderType) {
        orderFacade.createDemoOrder(tokenId, member.getMemberId(), OrderRequestDto.builder()
                .orderPrice(price)
                .orderQuantity(quantity)
                .orderType(orderType)
                .build());
    }
}
