// globals (yay vanilla javascript ftw)
fetchBlogs();
loginCheck();
document.getElementById("login-form")
    .addEventListener("submit", onLoginSubmit);
document.getElementById("logout-form")
    .addEventListener("submit", onLogoutSubmit);
document.getElementById("blog-form")
    .addEventListener("submit", onBlogSubmit);
let devToast = new bootstrap.Toast(
    document.getElementById("devToast"),
    { delay: 10000 }
);

// reads the XSRF-TOKEN cookie set by Spring Security's CookieCsrfTokenRepository
function getCsrfToken() {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function onLoginSubmit(event) {
  const username = event.target[0].value;
  const password = event.target[1].value;
  event.preventDefault();
  fetch("/api/user/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      "X-XSRF-TOKEN": getCsrfToken(),
    },
    body: new URLSearchParams({username, password}),
  })
      .then(filterOk)
      .then(response => response.json())
      .then(user => window.sessionStorage.setItem("fullname", user.fullname))
      .then(() => loginCheck());
}

function onLogoutSubmit(event) {
  event.preventDefault();
  fetch("/api/user/logout", {
    method: "POST",
    headers: {
      "X-XSRF-TOKEN": getCsrfToken(),
    },
  }).then(() => {
    window.sessionStorage.removeItem("fullname");
    loginCheck();
  });
}

function onBlogSubmit(event) {
  const data = {"title": event.target[0].value, "body": event.target[1].value};
  event.preventDefault();
  fetch("/api/blog", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCsrfToken(),
    },
    body: JSON.stringify(data),
  })
      .then(filterOk)
      .then(() => fetchBlogs())
      .then(() => event.target.reset());
}

// switch display based on login status
function loginCheck() {
  const fullname = window.sessionStorage.getItem("fullname") || "anonymous";
  let authentic = fullname !== "anonymous";
  document.getElementById("login-form").parentElement.hidden = authentic;
  document.getElementById("logout-form").parentElement.hidden = !authentic;
  document.getElementById("username").innerText = fullname;
}

function fetchBlogs() {
  fetch("/api/blog")
      .then(filterOk)
      .then(response => response.json())
      .then(page => renderBlogs(page.content));
}

// XSS fix: use textContent / createElement instead of innerHTML with user data
function renderBlogs(blogs) {
  const blogDiv = document.getElementById("blog-container");
  blogDiv.innerHTML = ""; // clear
  for (const blog of blogs) {
    const h2 = document.createElement("h2");
    h2.textContent = blog.title;

    const pDate = document.createElement("p");
    pDate.textContent = blog.createdAt;

    const pBody = document.createElement("p");
    pBody.textContent = blog.body;

    blogDiv.appendChild(h2);
    blogDiv.appendChild(pDate);
    blogDiv.appendChild(pBody);
  }
}

function showDevError(message) {
  document.getElementById("devToastText").textContent = message;
  devToast.show();
}

function filterOk(response) {
  if (response.ok) {
    return response;
  }
  return response.text().then(function(bodyText) {
    let msg = `HTTP ${response.status} ${response.statusText}`;
    if(bodyText && bodyText.length > 0){
      msg += `\n${bodyText.substring(0, 500)}`;
    }
    showDevError(msg);
    throw response;
  });
}
