# 유스케이스 흐름

각 흐름은 유스케이스 하나를 다룬다. 빨간 노드는 실패, `거절` 로 끝나면 되돌릴 것이 없고
`재고 복원` 을 거치면 되돌릴 것이 있다.

---

## 1. 주문

```mermaid
flowchart LR
    고객([고객]) --> A[주문 시작]
    A --> B[주문서 제작]
    B --> C[쿠폰 적용]
    C --> D[재고 차감]
    D --> E[쿠폰 소진]
    E --> F[주문 확정]
    F --> G{결제 수단 설정}
    G -->|포인트| H[포인트 차감]
    G -->|PG사| I{PG 승인}
    H --> J[결제 완료]
    I -->|승인| J
    J --> K[주문 완료]

    B --> B1["없는 상품<br/>삭제된 상품<br/>수량 0"]
    C --> C1[쿠폰 사용 불가]
    D --> D1[재고 부족]
    H --> H1[잔액 부족]
    I -->|거절·타임아웃| R[재고 복원]
    H1 --> R

    B1 --> X[거절]
    C1 --> X
    D1 --> X
    R --> X
    X -.-> 고객

    classDef fail stroke:#e06c75,color:#e06c75
    class B1,C1,D1,H1,R,X fail
```

쿠폰 적용과 쿠폰 소진은 선택 단계다. 쿠폰을 쓰지 않으면 둘 다 건너뛴다.

---

## 2. 주문 취소 — 확정 전

```mermaid
flowchart LR
    고객([고객]) --> A[취소 요청]
    A --> B[주문서 조회]
    B --> C[주문 취소 완료]

    B --> B1["없는 주문<br/>남의 주문<br/>이미 확정됨"]
    B1 --> X[거절]
    X -.-> 고객

    classDef fail stroke:#e06c75,color:#e06c75
    class B1,X fail
```

확정 전에는 재고를 잡지 않았으므로 복원할 것이 없다. 상태만 바꾼다.

---

## 3. 주문 취소 — 확정 후

```mermaid
flowchart LR
    고객([고객]) --> A[취소 요청]
    A --> B[주문서 조회]
    B --> C{결제 수단}
    C -->|포인트| D[포인트 복원]
    C -->|PG사| E[환불 요청]
    D --> F[재고 복원]
    E --> F
    F --> G[쿠폰 복원]
    G --> H[주문 취소 완료]

    B --> B1["없는 주문<br/>남의 주문<br/>이미 취소됨"]
    B1 --> X[거절]
    X -.-> 고객

    classDef fail stroke:#e06c75,color:#e06c75
    class B1,X fail
```

복원은 차감의 역순이다. 환불 실패 처리는 PG 도입 시 정한다.

---

## 4. 좋아요 등록

```mermaid
flowchart LR
    고객([고객]) --> A[좋아요 등록 요청]
    A --> B[상품 조회]
    B --> C[좋아요 등록]
    C --> D[완료]

    B --> B1["없는 상품<br/>삭제된 상품"]
    B1 -.->|exception| 고객
    C --> C1[이미 좋아요를 눌렀음]
    C1 --> D

    classDef fail stroke:#e06c75,color:#e06c75
    class B1 fail
```

---

## 5. 좋아요 해제

```mermaid
flowchart LR
    고객([고객]) --> A[좋아요 해제 요청]
    A --> B[좋아요 관계 조회]
    B --> C[좋아요 해제]
    C --> D[완료]

    B --> B1[좋아요가 이미 취소됨]
    B1 --> D
```

이미 그 상태여도 완료로 끝난다. 같은 요청을 여러 번 보내도 결과가 같다.

등록과 달리 상품의 존재·삭제 여부를 보지 않는다. 삭제된 상품에 남아 있는 자신의 관계도
취소할 수 있어야 하기 때문이다. 그래서 이 흐름에는 거절 경로가 없다.

---

## 6. 브랜드 삭제

```mermaid
flowchart LR
    관리자([관리자]) --> A[브랜드 삭제 요청]
    A --> B[브랜드 조회]
    B --> C{삭제되지 않은<br/>상품 확인}
    C -->|0| D[브랜드 삭제 완료]
    C -->|1 이상| C1[상품이 존재하는 경우]
    C1 --> C2["상품 = 판매중지 상태로<br/>변경"]
    C2 --> D

    B --> B1[브랜드가 존재하지 않는 경우]
    B1 -.->|exception| 관리자

    classDef fail stroke:#e06c75,color:#e06c75
    class B1 fail
```

**상품이 남아 있어도 브랜드를 삭제한다.** 대신 그 브랜드의 상품을 판매중지 상태로 바꾼다.
거절 경로는 브랜드가 없는 경우 하나뿐이다.

---

## 7. 상품 삭제

```mermaid
flowchart LR
    관리자([관리자]) --> A[상품 삭제 요청]
    A --> B[상품 조회]
    B --> C[좋아요 삭제]
    C --> D[상품 삭제]

    B --> B1["상품이 존재하지 않는 경우<br/>재고가 존재하는 경우"]
    B1 -.->|exception| 관리자

    classDef fail stroke:#e06c75,color:#e06c75
    class B1 fail
```

---

## 8. 포인트 충전

```mermaid
flowchart LR
    고객([고객]) --> A[포인트 충전 요청]
    A --> B[잔액 증가]
    B --> C["포인트 충전 완료<br/>기존 잔액 + 충전액"]

    B --> B1[음수인 경우]
    B1 -.->|exception| 고객

    classDef fail stroke:#e06c75,color:#e06c75
    class B1 fail
```


---

## 9. 브랜드 생성

```mermaid
flowchart LR
    관리자([관리자]) --> A[브랜드 생성 요청]
    A --> B[브랜드 명 확인]
    B --> C[브랜드 생성]

    B --> B1["브랜드 명은 공백일 수 없습니다.<br/>브랜드명은 4자리 이상 8자리 이하입니다."]
    B1 -.->|exception| 관리자

    classDef fail stroke:#e06c75,color:#e06c75
    class B1 fail
```

---

## 10. 브랜드 상세 조회

```mermaid
flowchart LR
    관리자([관리자]) --> A[브랜드 조회 요청]
    A --> B[브랜드 조회]
    B --> C[브랜드 상세 조회]

    B --> B1[존재하지 않는 브랜드인 경우]
    B1 -.->|Exception| 관리자

    classDef fail stroke:#e06c75,color:#e06c75
    class B1 fail
```

---

## 11. 브랜드 목록 조회

```mermaid
flowchart LR
    관리자([관리자]) --> A[브랜드 목록 조회 요청]
    A --> B[브랜드 목록 조회]
```

아무 브랜드도 없는 경우 빈 List 를 반환한다. 거절 경로가 없다.

---

## 12. 브랜드 수정

```mermaid
flowchart LR
    관리자([관리자]) --> A[브랜드 수정 요청]
    A --> B[브랜드 조회]
    B --> C[브랜드 수정]

    B --> B1[존재하지 않는 브랜드인 경우]
    B1 -.->|Exception| 관리자

    classDef fail stroke:#e06c75,color:#e06c75
    class B1 fail
```

---

## 13. 상품 생성

```mermaid
flowchart LR
    관리자([관리자]) --> A[상품 생성 요청]
    A --> B[상품 명 확인]
    B --> C[상품 생성]
    C --> D["재고 생성<br/>초기값: 0"]

    B --> B1["상품 명은 공백일 수 없습니다.<br/>상품명은 2자리 이상 20자리 이하입니다."]
    B1 -.->|exception| 관리자

    classDef fail stroke:#e06c75,color:#e06c75
    class B1 fail
```

재고는 생성에서 받지 않는다. 항상 0으로 시작하고 재고 변경으로만 바꾼다.

---

## 14. 상품 상세 조회

```mermaid
flowchart LR
    관리자([관리자]) --> A[상품 조회 요청]
    A --> B[상품 조회]
    B --> C[상품 상세 조회]

    B --> B1[존재하지 않는 상품인 경우]
    B1 -.->|Exception| 관리자

    classDef fail stroke:#e06c75,color:#e06c75
    class B1 fail
```

상세에서 좋아요 수와 재고를 확인할 수 있다.

---

## 15. 상품 목록 조회

```mermaid
flowchart LR
    관리자([관리자]) --> A[상품 목록 조회 요청]
    A --> B[상품 목록 조회]
```

좋아요 갯수를 확인할 수 있다. 아무 상품도 없는 경우 빈 List 를 반환한다.

---

## 16. 상품 수정

```mermaid
flowchart LR
    관리자([관리자]) --> A[상품 수정 요청]
    A --> B[상품 조회]
    B --> C[상품 수정]

    B --> B1["존재하지 않는 상품인 경우<br/>판매중지인 상품인 경우"]
    B1 -.->|Exception| 관리자

    classDef fail stroke:#e06c75,color:#e06c75
    class B1 fail
```

---

## 17. 상품 재고 변경

```mermaid
flowchart LR
    관리자([관리자]) --> A[재고 변경 요청]
    A --> B[상품 조회]
    B --> C[재고 증가 값 입력]
    C --> D[상품 재고 수량 변경]

    B --> B1["존재하지 않는 상품인 경우<br/>판매중지인 상품인 경우"]
    C --> C1[음수인 경우]
    B1 -.->|Exception| 관리자
    C1 -.->|Exception| 관리자

    classDef fail stroke:#e06c75,color:#e06c75
    class B1,C1 fail
```

최종 수량을 설정하는 것이 아니라 **증가 값**을 받는다. 음수만 거절하므로 0 은 통과하고
재고가 바뀌지 않는다.

---

## 18. 주문 목록 조회 — 관리자

```mermaid
flowchart LR
    관리자([관리자]) --> A[주문서 목록 조회 요청]
    A --> B[주문 목록 조회]
```

아무것도 없는 경우 빈 리스트를 리턴한다.

---

## 19. 주문 상세 조회 — 관리자

```mermaid
flowchart LR
    관리자([관리자]) --> A[주문서 조회 요청]
    A --> B[주문 조회]
    B --> C[특정 주문 확인]

    B --> B1[주문이 존재하지 않는 경우]
    B1 -.->|exception| 관리자

    classDef fail stroke:#e06c75,color:#e06c75
    class B1 fail
```

관리자는 소유자를 가리지 않고 조회한다.

---

## 20. 주문 상세 조회 — 고객

```mermaid
flowchart LR
    고객([고객]) --> A[주문서 조회 요청]
    A --> B[주문 조회]
    B --> C[내 주문 확인]

    B --> B1["내 주문이 아닌 경우<br/>주문이 존재하지 않는 경우"]
    B1 -.->|exception| 고객

    classDef fail stroke:#e06c75,color:#e06c75
    class B1 fail
```

하위에 주문 line 이 있다. 주문 line 은 `상품 × 수량` 이다.

**내 주문이 아닌 경우와 존재하지 않는 경우가 같은 박스다.** 두 응답이 구별되지 않아야
남의 주문이 있다는 사실이 새어나가지 않는다.

---

## 21. 상품 목록 조회 — 고객

```mermaid
flowchart LR
    고객([고객]) --> A[상품 목록 조회 요청]
    A --> B["필터링<br/>- 브랜드 필터<br/>- 페이지 조회<br/>latest · price_asc · likes_desc 정렬"]
    B --> C["상품 목록 조회<br/>+ 재고 수량<br/>+ 좋아요 갯수"]
```

필터링에 걸리지 않는 경우 빈 리스트를 반환한다.

---

## 22. 좋아요 목록 조회 — 고객

```mermaid
flowchart LR
    고객([고객]) --> A[좋아요 목록 조회 요청]
    A --> B[상품 + 좋아요 확인]
    B --> C["내가 좋아요를 누른<br/>상품 리스트 확인"]

    B --> B1[내 좋아요가 아닌 경우]
    B1 -.->|exception| 고객

    classDef fail stroke:#e06c75,color:#e06c75
    class B1 fail
```

좋아요가 하나도 없는 경우 빈 리스트를 반환한다.

삭제된 상품은 **거절하지 않고 목록에서 제외한다.** 거절하면 좋아요해 둔 상품이 삭제된 순간
그 사용자는 목록을 볼 수 없게 되고, 목록을 못 보니 그 좋아요를 취소할 수도 없다.

---

## 23. 주문 생성

```mermaid
flowchart LR
    고객([고객]) --> A[주문 생성 요청]
    A --> B["주문 LINE 등록<br/>상품 * 갯수"]
    B --> C["주문 생성 완료<br/>(주문: DRAFT)"]
```

재고는 차감하지 않는다. 차감은 주문 확정에서 한다.

---

## 24. 주문 확정

```mermaid
flowchart LR
    고객([고객]) --> A[주문 확정 요청]
    A --> B[주문 조회]

    B --> B0{본인 주문<br/>존재 여부}
    B0 -->|Y| B1[주문 상태가 DRAFT 가 아닌 경우]
    B0 -->|N| B2["본인 주문이 아닌 경우<br/>주문이 존재하지 않는 경우"]

    subgraph TX["하나의 트랜잭션 — 전부 반영되거나 전부 취소된다"]
        direction LR
        C[재고 감소] --> D[포인트 차감]
        D --> E["주문 확정 완료<br/>(CONFIRMED)"]
    end

    B --> C
    C --> C1[재고가 없는 경우]
    C --> C2["상품이 존재하지 않거나<br/>판매 중지인 경우"]
    D --> D1[잔액 부족]

    B1 -.->|409 CONFLICT| 고객
    B2 -.->|404 NOT FOUND| 고객
    C1 -.->|400 BAD REQUEST| 고객
    C2 -.->|404 NOT FOUND| 고객
    D1 -.->|400 BAD REQUEST| 고객

    classDef fail stroke:#e06c75,color:#e06c75
    class B1,B2,C1,C2,D1 fail
```

재고를 먼저 줄이고 포인트를 마지막에 차감한다. 되돌리기 싼 것이 앞이다.

상품 존재·판매중지는 **확정에서 다시 확인한다.** 주문서를 만든 뒤 상품이 내려갈 수 있다.

묶음 안의 어느 단계에서 실패해도 **앞 단계가 함께 취소된다.** 잔액이 부족하면 줄인 재고가
되돌아가고 주문은 DRAFT 로 남는다. 그래서 되돌림을 단계로 그리지 않았다.
