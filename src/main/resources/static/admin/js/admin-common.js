// 어드민 페이지 공용 스크립트 — CSRF 포함 fetch 래퍼 + 공통 네비게이션

function getCsrfTokenFromCookie() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
}

async function adminFetch(path, options) {
    const opts = options || {};
    opts.headers = Object.assign({}, opts.headers, { 'X-XSRF-TOKEN': getCsrfTokenFromCookie() });
    const response = await fetch(path, opts);
    if (response.status === 401 || response.status === 403) {
        location.href = '/admin/login.html';
        throw new Error('인증 필요');
    }
    return response;
}

const ADMIN_NAV_ITEMS = [
    { key: 'maintenance', label: '점검 모드', href: '/admin/maintenance.html' },
    { key: 'users', label: '유저 통계', href: '/admin/users.html' },
    { key: 'concurrent', label: '동시접속자', href: '/admin/concurrent-users.html' },
];

function renderAdminNav(activeKey) {
    const container = document.getElementById('adminNav');
    if (container === null) return;

    const links = ADMIN_NAV_ITEMS.map((item) => {
        const activeStyle = item.key === activeKey ? 'font-weight:bold;color:#4a7dff;' : 'color:#ccc;';
        return `<a href="${item.href}" style="margin-right:16px;text-decoration:none;${activeStyle}">${item.label}</a>`;
    }).join('');

    container.innerHTML = `
        <div style="display:flex;align-items:center;padding:12px 20px;background:#262936;border-bottom:1px solid #333;">
            <span style="margin-right:24px;font-weight:bold;color:#eee;">운영툴</span>
            ${links}
            <form method="post" action="/admin/logout" style="margin-left:auto;">
                <input type="hidden" name="_csrf" value="${getCsrfTokenFromCookie()}">
                <button type="submit" style="background:none;border:none;color:#888;cursor:pointer;padding:0;font-size:14px;">로그아웃</button>
            </form>
        </div>
    `;
}
