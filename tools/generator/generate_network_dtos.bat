사용법
ModuleBody 프리팹에 HexShieldGrid 컴포넌트 추가
m_PointParent에 빈 GameObject 할당 (꼭지점들이 여기 하위로 생성됨)
Inspector에서 "Generate Hex Shield" 버튼 클릭
설정값
파라미터	설명	권장값
cellCount	헥사곤 셀 개수	24~48
boundMargin	바운드 여백	0.2~0.5
boundScale	바운드 배율	1.1~1.3
구조

셀 중심점 (주황색 ●)     꼭지점 (시안색 ●)
        ●                    ●───●
       / \                  / \ / \
      ●   ●                ●   ●   ●
       \ /                  \ / \ /
        ●                    ●───●

각 꼭지점은 정확히 3개의 이웃과 연결됨
AirCraftPath 대체
HexVertex.GetPosition()으로 비행 경로 포인트 사용 가능. 기존 AirCraftPathPoint와 동일한 역할.

테스트해보고 결과 알려주세요. 삼각분할 알고리즘이 단순화 버전이라 일부 불규칙한 패턴이 나올 수 있는데, 그 경우 개선하겠습니다.