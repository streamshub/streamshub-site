+++
title = '{{ sourceName }} Documentation'
linkTitle = "{{ linkTitle }}"

+++
{# Redirect straight to the in-development docs instead of showing a "pick a version" landing
   page - most readers want the latest content, not a version-picker detour. The sidebar
   version picker (or the released-versions list below, for non-JS readers) is how you get to
   an older tagged version instead. #}
<div id="__redirect-target" style="display:none">

[{{ developmentBranchName }}]({{ developmentBranchIndexFile }})

</div>
<script>
(function () {
  var a = document.querySelector('#__redirect-target a');
  if (a) { window.location.replace(a.href); }
})();
</script>
<noscript>

## In development documentation

[{{ developmentBranchName }}]({{ developmentBranchIndexFile }})

## Released versions documentation
{% for tag in tags %}
[{{ tag.name }}]({{ tag.indexFile }})
{% endfor %}

</noscript>
