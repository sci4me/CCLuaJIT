table.pack = function(...)
    return { ..., n = select('#', ...) }
end