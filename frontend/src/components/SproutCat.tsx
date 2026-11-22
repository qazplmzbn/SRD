export type CatMood='idle'|'nuzzle'|'snack'|'play'|'sleep';

export default function SproutCat({mood,size}:{mood:CatMood;size:number}){
  const closed=mood==='sleep'||mood==='nuzzle'||mood==='snack';
  return <svg aria-hidden="true" viewBox="0 0 88 88" width={size} height={size} fill="none" stroke="#654e45" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
    <ellipse cx="44" cy="80" rx="30" ry="4" fill="#456e5d" opacity=".15" stroke="none"/>
    <path d="M58 74Q75 75 75 60Q74 51 80 53Q88 57 82 71Q76 83 58 79Z" fill="#e9b58f"/>
    <path d="M23 56Q16 67 21 76Q20 82 34 79Q44 81 52 79Q68 82 67 75Q72 64 60 54Z" fill="#fff0d9"/>
    <path d="M24 54Q43 63 63 54L52 69L44 64L37 68Z" fill="#6bb6a2"/>
    <path d="M14 36L16 16Q16 12 20 14L33 23Q43 19 54 22L68 13Q72 11 72 17L74 37Q81 47 72 56Q63 65 44 63Q22 65 13 54Q7 46 14 36Z" fill="#fff3df"/>
    <path d="M17 33L19 18L29 25Z" fill="#e9aa96" stroke="none"/><path d="M60 25L69 17L70 32Z" fill="#e9aa96" stroke="none"/>
    <path d="M33 24Q36 31 38 25M42 23Q45 32 47 23" stroke="#e7b88d" strokeWidth="3.5"/>
    <path d="M43 22Q45 15 41 9M43 17Q44 6 56 5Q56 15 43 17Z" fill="#84bb83" stroke="#477e62"/><path d="M42 13Q32 15 30 6Q39 4 42 13Z" fill="#b9d591" stroke="#477e62"/>
    <g fill="#eeb9a8" stroke="none"><ellipse cx="23" cy="50" rx="5" ry="2.8"/><ellipse cx="65" cy="50" rx="5" ry="2.8"/></g>
    {closed?<path d={mood==='sleep'?'M25 44q4 5 9 0 M53 44q5 5 10 0':'M25 46q4-7 9-1 M53 45q5-7 10 0'}/>:<g fill="#493c38" stroke="none"><ellipse cx="30" cy="44" rx="3.5" ry="4.4"/><ellipse cx="58" cy="44" rx="3.5" ry="4.4"/><path d="M29 42h.5m27.5 0h.5" stroke="white"/></g>}
    <path d="M41 48q3-2 6 0l-3 3Z" fill="#b67e70" stroke="none"/><path d="M44 51q-2 4-5 1m5-1q2 4 5 1" strokeWidth="1.6"/>
    <path d="M12 46l7 1m-6 5 6-1m51-4 6-1m-6 5 6 1" strokeWidth="1.6"/>
    {mood==='snack'&&<path d="M34 61q9-12 18-2l7-4-1 12-7-4q-10 7-17-2Z" fill="#de9367"/>}
    {mood==='play'&&<g><circle cx="17" cy="72" r="10" fill="#d995ad"/><path d="M10 66q9 1 14 9m-16-5q9 2 13 11m-1-19q1 5-2 7" stroke="#996078" strokeWidth="1.6"/></g>}
    {mood==='sleep'&&<path d="M69 7h7l-7 7h7m2-13h5l-5 5h5" stroke="#577c88"/>}
  </svg>;
}