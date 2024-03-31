
export function Input({ label, setState }) {
  let comp = (
    <>
      <label>{label}</label>
      <input
        type="text"
        id={label}
        name={label}
        onChange={e => setState(e.target.value)}
      />
    </>
  );
  return comp;
}