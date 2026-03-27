# Server-Sent Events (SSE)
```html
<button data-on:click="@get('/endpoint')"></button>
```
```
event: datastar-patch-elements
data: elements <div id="foo">Hello world!</div>

event: datastar-patch-elements
data: mode append
data: selector body
data: elements <div>
data: elements       I am appended! </div>
```
