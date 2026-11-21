import "../style/global.css";

export function clearPage() {
  var appDiv = document.getElementById("app-div");

  if (appDiv == null) throw new Error("Element app-div not found");

  appDiv.innerHTML = "";
}

export function loadNavigator(nodes: Node[]) {
  console.log("Loading navigator");

  var appDiv = document.getElementById("app-div");

  if (appDiv == null) throw new Error("Element app-div not found");

  var navigatorDiv = document.createElement("div");
  navigatorDiv.className = "navigator-div";

  var navigatorUl = document.createElement("ul");
  navigatorUl.className = "navigator-ul";

  var navigatorLis = nodes.map((n) => {
    var navigatorLi = document.createElement("li");
    navigatorLi.className = "navigator-li";
    navigatorLi.style = "margin-bottom: 16px;";
    navigatorLi.appendChild(n);

    return navigatorLi;
  });

  var topNavigatorLi = document.createElement("li");
  topNavigatorLi.className = "navigator-li";
  var topTitle = document.createElement("div");
  for (var e of document.head.children)
    if (e instanceof HTMLTitleElement) topTitle.innerHTML = e.innerHTML;

  topTitle.style =
    "text-align: center; color: #6060ff; margin-top: 16px; margin-bottom: 16px;";

  topNavigatorLi.append(topTitle);
  navigatorUl.append(topNavigatorLi);
  navigatorLis.forEach((li) => navigatorUl.append(li));
  navigatorDiv.append(navigatorUl);
  appDiv.append(navigatorDiv);
}

export function setTitle(t: string) {
  for (var e of document.head.children)
    if (e instanceof HTMLTitleElement) e.innerHTML = t;
}

export function toast(n: Node) {
  var toastDiv = document.createElement("div");
  toastDiv.className = "toast-div";
  toastDiv.append(n);
  toastDiv.onclick = () => document.body.removeChild(toastDiv);

  document.body.append(toastDiv);
}

export function toastHtml(s: string) {
  var toastDiv = document.createElement("div");
  toastDiv.className = "toast-div";
  toastDiv.innerHTML = s;
  toastDiv.onclick = () => document.body.removeChild(toastDiv);

  document.body.append(toastDiv);
}

export function getAppDiv(): HTMLElement {
  var div = document.getElementById("app-div");
  if (div == null) throw new Error("Element app-div not found");

  return div;
}
